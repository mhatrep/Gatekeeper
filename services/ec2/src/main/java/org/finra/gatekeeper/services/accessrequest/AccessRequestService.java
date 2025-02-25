/*
 * Copyright 2018. Gatekeeper Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finra.gatekeeper.services.accessrequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.impl.persistence.entity.HistoricVariableInstanceEntity;
import org.activiti.engine.task.Task;
import org.finra.gatekeeper.common.services.account.AccountInformationService;
import org.finra.gatekeeper.common.services.account.model.Account;
import org.finra.gatekeeper.common.services.eventlogging.RequestEventLogger;
import org.finra.gatekeeper.common.services.user.model.GatekeeperUserEntry;
import org.finra.gatekeeper.configuration.properties.GatekeeperApprovalProperties;
import org.finra.gatekeeper.controllers.AccessRequestController;
import org.finra.gatekeeper.controllers.wrappers.AccessRequestWrapper;
import org.finra.gatekeeper.controllers.wrappers.ActiveAccessRequestWrapper;
import org.finra.gatekeeper.controllers.wrappers.CompletedAccessRequestWrapper;
import org.finra.gatekeeper.exception.GatekeeperException;
import org.finra.gatekeeper.services.accessrequest.model.*;
import org.finra.gatekeeper.services.accessrequest.model.messaging.dto.RequestEventDTO;
import org.finra.gatekeeper.services.accessrequest.model.messaging.enums.EventType;
import org.finra.gatekeeper.services.accessrequest.model.messaging.dto.ActiveAccessRequestDTO;
import org.finra.gatekeeper.services.accessrequest.model.messaging.dto.UserInstancesDTO;
import org.finra.gatekeeper.services.accessrequest.model.messaging.dto.ActiveRequestUserDTO;
import org.finra.gatekeeper.services.auth.GatekeeperRole;
import org.finra.gatekeeper.services.auth.GatekeeperRoleService;
import org.finra.gatekeeper.services.aws.SnsService;
import org.finra.gatekeeper.services.aws.SsmService;
import org.finra.gatekeeper.services.aws.model.AWSEnvironment;
import org.finra.gatekeeper.services.email.wrappers.EmailServiceWrapper;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.internal.NativeQueryImpl;
import org.hibernate.transform.AliasToEntityMapResultTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that is used for various activities around the AccessRequest Object
 */

@Component
public class AccessRequestService {

    private static final Logger logger = LoggerFactory.getLogger(AccessRequestController.class);
    private final TaskService taskService;
    private final AccessRequestRepository accessRequestRepository;
    private final GatekeeperRoleService gatekeeperRoleService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final GatekeeperApprovalProperties approvalPolicy;
    private final AccountInformationService accountInformationService;
    private final EmailServiceWrapper emailServiceWrapper;
    private final SsmService ssmService;
    private final SnsService snsService;
    private final EntityManager entityManager;
    private final String REJECTED = "REJECTED";
    private final String APPROVED = "APPROVED";
    private final String CANCELED = "CANCELED";

    protected static final StringBuilder REQUEST_QUERY = new StringBuilder()
            .append("select access_request.id,\n")
            .append("       access_request.account,\n")
            .append("       access_request.requestor_name,\n")
            .append("       access_request.requestor_email,\n")
            .append("       access_request.requestor_id,\n")
            .append("       access_request.request_reason,\n")
            .append("       access_request.approver_comments,\n")
            .append("       access_request.actioned_by_user_name,\n")
            .append("       access_request.actioned_by_user_id,\n")
            .append("       access_request.hours,\n")
            .append("       access_request.platform,\n")
            .append("       user_count,\n")
            .append("       created,\n")
            .append("       updated,\n")
            .append("       instance_count,\n")
            .append("       status\n")
            .append("from gatekeeper.access_request access_request,\n")
            .append("  -- This gets the access request id and their created time, their updated time and their request status from activiti tables\n")
            .append("  (select cast(text2_ as numeric) access_request_id, create_time_ as created, last_updated_time_ as updated, status\n")
            .append("   from (select a.proc_inst_id_, a.text2_\n")
            .append("         from gatekeeper.act_hi_varinst a\n")
            .append("         where name_ = 'accessRequest') accessRequestId,\n")
            .append("        -- This gets the request status\n")
            .append("        (select a.proc_inst_id_,\n")
            .append("                a.create_time_,\n")
            .append("                a.last_updated_time_,\n")
            .append("                substring(encode(b.bytes_, 'escape'), '\\w+$') as status\n")
            .append("         from gatekeeper.act_hi_varinst a\n")
            .append("                join gatekeeper.act_ge_bytearray b on a.bytearray_id_ = b.id_) accessRequestStatus\n")
            .append("   where accessRequestId.proc_inst_id_ = accessRequestStatus.proc_inst_id_\n")
            .append("  ) gk_activiti,\n")
            .append("  -- This counts the users oer request\n")
            .append("  (select a.id, count(*) as user_count\n")
            .append("   from gatekeeper.access_request a,\n")
            .append("        gatekeeper.access_request_users b\n")
            .append("   where a.id = b.access_request_id\n")
            .append("   group by a.id) users,\n")
            .append("  -- This counts the dbs per request\n")
            .append("  (select a.id, count(*) as instance_count\n")
            .append("   from gatekeeper.access_request a,\n")
            .append("        gatekeeper.access_request_instances b\n")
            .append("   where a.id = b.access_request_id\n")
            .append("   group by a.id) instances\n")
            .append("where access_request.id = gk_activiti.access_request_id\n")
            .append("  and access_request.id = users.id\n")
            .append("  and access_request.id = instances.id\n");

    protected static final String INSTANCE_QUERY = "SELECT id, application, instances_id, ip, name, platform, status\n" +
            "FROM gatekeeper.access_request_instances w, gatekeeper.request_instance c\n" +
            "WHERE w.access_request_id = :request_id \n" +
            "AND w.instances_id = c.id \n";
    protected static final String USER_QUERY = "SELECT id, name, user_id, email\n" +
            "FROM gatekeeper.access_request_users a, gatekeeper.request_user r\n" +
            "WHERE a.access_request_id = :request_id \n" +
            "AND a.users_id = r.id;";

    @Autowired
    public AccessRequestService(TaskService taskService,
                                AccessRequestRepository accessRequestRepository,
                                GatekeeperRoleService gatekeeperRoleService,
                                RuntimeService runtimeService,
                                HistoryService historyService,
                                AccountInformationService accountInformationService,
                                EmailServiceWrapper emailServiceWrapper,
                                SsmService ssmService,
                                SnsService snsService,
                                GatekeeperApprovalProperties gatekeeperApprovalProperties,
                                EntityManager entityManager){

        this.taskService = taskService;
        this.accessRequestRepository = accessRequestRepository;
        this.gatekeeperRoleService = gatekeeperRoleService;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.accountInformationService = accountInformationService;
        this.emailServiceWrapper = emailServiceWrapper;
        this.ssmService = ssmService;
        this.snsService = snsService;
        this.approvalPolicy = gatekeeperApprovalProperties;
        this.entityManager = entityManager;
    }

    public AccessRequest storeAccessRequest(AccessRequestWrapper request) throws GatekeeperException {
        GatekeeperUserEntry requestor = gatekeeperRoleService.getUserProfile();

        //Validating that all instances in the request are the same as the requested platform
        //This also means that all instances have the same platform.
        List<String> instanceIds = new ArrayList<>();
        for(AWSInstance instance : request.getInstances()){
            if(!instance.getPlatform().equals(request.getPlatform())){
                throw new GatekeeperException("Instance platform doesn't match requested platform. Instance: "
                        + instance.getPlatform() + " Requested: " +request.getPlatform());
            }
            instanceIds.add(instance.getInstanceId());
        }
        AWSEnvironment environment = new AWSEnvironment(request.getAccount(),request.getRegion());
        String invalidInstances = ssmService.checkInstancesAreValidWithSsm(environment, instanceIds);
        if(!invalidInstances.isEmpty()){
            throw new GatekeeperException(invalidInstances);
        }
        //throw gk in front of all the user id's
        request.getUsers().forEach(u -> u.setUserId("gk-" + u.getUserId()));

        AccessRequest accessRequest = new AccessRequest()
                .setAccount(request.getAccount().toUpperCase())
                .setRegion(request.getRegion())
                .setHours(request.getHours())
                .setRequestorId(requestor.getUserId())
                .setRequestorName(requestor.getName())
                .setRequestorEmail(requestor.getEmail())
                .setUsers(request.getUsers())
                .setInstances(request.getInstances())
                .setTicketId(request.getTicketId())
                .setRequestReason(request.getRequestReason())
                .setPlatform(request.getPlatform());

        logger.info("Storing Access Request");
        accessRequestRepository.save(accessRequest);
        logger.info("Access Request stored with ID: " + accessRequest.getId());

        //Kick off the activiti workflow

        Map<String, Object> variables = new HashMap<>();
        variables.put("accessRequest", accessRequest);
        runtimeService.startProcessInstanceByKey("gatekeeperAccessRequest", variables);

        // Verify that we started a new process instance
        logger.info("Number of process instances: " + runtimeService.createProcessInstanceQuery().count());

        try {
            boolean approvalNeeded = isApprovalNeeded(accessRequest);
            boolean topicSet = snsService.isEmailTopicSet();
            if (approvalNeeded && topicSet) {
                snsService.pushToEmailSNSTopic(accessRequest);
            } else if (!approvalNeeded){
                logger.info("Approval is not required for this request (" + accessRequest.getId() + "). Skipping publishing of access request to SNS topic.");
            } else {
                logger.info("SNS topic ARN not provided. Skipping publishing of access request to SNS topic.");
            }
        } catch (Exception e) {
            Long accessRequestId = accessRequest.getId();
            emailServiceWrapper.notifyAdminsOfFailure(accessRequest, e);
            logger.error("Unable to push access request (" + accessRequestId + ") to SNS topic.");
        }
        RequestEventLogger.logEventToJson(org.finra.gatekeeper.common.services.eventlogging.EventType.AccessRequested, accessRequest);
        return accessRequest;
    }

    private boolean isRequestorOwnerOfInstances(AccessRequest request) {
        Set<String> memberships = gatekeeperRoleService.getMemberships();
        for (AWSInstance instance : request.getInstances()) {
            if (memberships == null ||  !memberships.contains(instance.getApplication())) {
                //return false because there exists an instance the requestor doesn't "own"
                return false;
            }
        }
        //if we didn't find any instances the requestor didn't have an Application for we return true
        return true;
    }

    public boolean isApprovalNeeded(AccessRequest request) throws Exception{
        Map<String, Integer> policy = approvalPolicy.getApprovalPolicy(gatekeeperRoleService.getRole());

        //We have to associate the policy to the SDLC of the requested account. The name of the account provided by the ui will not always be "dev" "qa" or "prod", but they will need to associate with those SDLC's
        Account theAccount = accountInformationService.getAccountByAlias(request.getAccount());

        switch(gatekeeperRoleService.getRole()){
            case APPROVER:
                return false;
            case SUPPORT:
                return request.getHours() > policy.get(theAccount.getSdlc().toLowerCase());
            case AUDITOR:
            case DEV:
            case OPS:
                return request.getHours() > policy.get(theAccount.getSdlc().toLowerCase()) || !isRequestorOwnerOfInstances(request);
            default:
                //should NEVER happen.
                throw new Exception("Could not determine Role");
        }
    }


    private void handleRequest(String user, String taskId, RequestStatus status) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("requestStatus", status);

        //todo: samAccountName is the approver
        taskService.setAssignee(taskId, user);
        taskService.complete(taskId, vars);
    }


    public List<ActiveAccessRequestWrapper> getActiveRequests() {
        List<Task> tasks = taskService.createTaskQuery().active().list();
        List<ActiveAccessRequestWrapper> response = new ArrayList<>();
        tasks.forEach(task -> {
            AccessRequest theRequest = updateInstanceStatus(
                    accessRequestRepository.getAccessRequestById(Long.parseLong(
                            runtimeService.getVariableInstance(task.getExecutionId(), "accessRequest").getTextValue2())
                    ));
            response.add(new ActiveAccessRequestWrapper(theRequest)
                    .setCreated(task.getCreateTime())
                    .setTaskId(task.getId())
                    .setInstanceCount(theRequest.getInstances().size())
                    .setUserCount(theRequest.getUsers().size()));
        });

        return (List<ActiveAccessRequestWrapper>)filterResults(response);
    }


    public List<CompletedAccessRequestWrapper> getRequest(Long id) {
        final ObjectMapper mapper = new ObjectMapper();
        List<CompletedAccessRequestWrapper> results = new ArrayList<>();

        NativeQueryImpl q = (NativeQueryImpl) entityManager.createNativeQuery(REQUEST_QUERY
                +"and access_request.id = :request_id \n"
                +"order by updated desc;");
        q.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);
        q.setParameter("request_id", id);

        NativeQueryImpl instanceQuery =  (NativeQueryImpl) entityManager.createNativeQuery(INSTANCE_QUERY);
        instanceQuery.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);
        instanceQuery.setParameter("request_id", id);

        NativeQueryImpl userQuery = (NativeQueryImpl) entityManager.createNativeQuery(USER_QUERY);
        userQuery.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);
        userQuery.setParameter("request_id", id);

        List < Map < String, AccessRequestWrapper >> result = q.getResultList();
        List < Map < String, User >> userResult = userQuery.getResultList();
        List < Map < String, AWSInstance >> instanceResult = instanceQuery.getResultList();

        for (Map map: result) {
            CompletedAccessRequestWrapper requestWrapper = mapper.convertValue(map, CompletedAccessRequestWrapper.class);

            for (Map instanceMap: instanceResult) {
                AWSInstance instance = mapper.convertValue(instanceMap, AWSInstance.class);
                requestWrapper.getInstances().add(instance);
            }

            for (Map userMap: userResult) {
                User user = mapper.convertValue(userMap, User.class);
                requestWrapper.getUsers().add(user);
            }

            results.add(requestWrapper);
        }

        return (List<CompletedAccessRequestWrapper>)filterResults(results);
    }


    public List<CompletedAccessRequestWrapper> getCompletedRequests() {
        /*
            This object is all of the Activiti Variables associated with the request
            This map will contain the following:
            When the request was opened
            When the request was actioned by an approver (or canceled by a user/approver)
            How many attempts it took to approve the user
         */
        final ObjectMapper mapper = new ObjectMapper();
        List<CompletedAccessRequestWrapper> results = new ArrayList<>();

        String query = REQUEST_QUERY + "ORDER BY updated DESC;";

        NativeQueryImpl q = (NativeQueryImpl) entityManager.createNativeQuery(query);
        q.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);

        List < Map < String, String >> result = q.getResultList();
        for (Map map: result) {
            CompletedAccessRequestWrapper requestWrapper = mapper.convertValue(map, CompletedAccessRequestWrapper.class);
            results.add(requestWrapper);
        }

        return (List<CompletedAccessRequestWrapper>)filterResults(results);
    }


    /**
     *
     * @param eventType     Type of event that invokes this method. Allowed values are: EventType.APPROVAL and EventType.EXPIRATION
     * @param request If a request is expiring, this parameter contains the request information for the expired request.
     * @return              A list of all live and recently expired access requests.
     */
    public RequestEventDTO getLiveRequestsForUsersInRequest(EventType eventType, AccessRequest request) {
        logger.info("Fetching live requests.");

        // This will get back all request data that is in the current time - maximum_request_value time window
        logger.info("Compiling list of all live requests.");
        List<CompletedAccessRequestWrapper> activitiData = getLiveRequests();
        logger.info("There are " + activitiData.size() + " Requests that are live");

        if(eventType == EventType.APPROVAL) {
            activitiData.add(new CompletedAccessRequestWrapper(request));
        }
        List<ActiveRequestUserDTO> activeRequestUserList = constructLiveRequestMessageForUsers(activitiData, request.getUsers());
        logger.info("Successfully compiled live requests.");
        if(eventType == EventType.EXPIRATION) {
            logger.info("Adding expired request to response object.");
            activeRequestUserList = addExpiredRequest(activeRequestUserList, request);
            logger.info("Successfully added expired request to response object.");
        }

        return new RequestEventDTO()
                .setRequestId(request.getId())
                .setEventType(eventType.getValue())
                .setUsers(activeRequestUserList);
    }

    public AccessRequest updateInstanceStatus(AccessRequest accessRequest){
        AWSEnvironment environment = new AWSEnvironment(accessRequest.getAccount(),accessRequest.getRegion());
        List<AWSInstance> requestedInstances = accessRequest.getInstances();
        List<String> instanceIds = requestedInstances.stream().map(instance -> instance.getInstanceId()).collect(Collectors.toList());
        Map<String,String> instances = ssmService.checkInstancesWithSsm(environment, instanceIds);
        requestedInstances.forEach(instance ->
                instance.setStatus(instances.get(instance.getInstanceId()) != null ? instances.get(instance.getInstanceId()) : "Unknown")
        );
        accessRequest.setInstances(requestedInstances);
        accessRequestRepository.save(accessRequest);

        return accessRequest;

    }


    /**
     * Helper function to update the request comments / actionedBy fields for the access request
     *
     * @param requestId - the access request ID
     * @param approverComments - the comments from the approver
     * @param action - the action taken on the request
     */
    private void updateRequestDetails(Long requestId, String approverComments, String action){
        AccessRequest accessRequest = accessRequestRepository.getAccessRequestById(requestId);
        accessRequest.setApproverComments(approverComments);
        GatekeeperUserEntry user = gatekeeperRoleService.getUserProfile();
        accessRequest.setActionedByUserId(user.getUserId());
        accessRequest.setActionedByUserName(user.getName());
        accessRequestRepository.save(accessRequest);
        logger.info("Access Request " + accessRequest.getId() + " was " + action +" by " + user.getName() + " (" + user.getUserId() +"). ");

    }

    /**
     * Helper function to update the request comments / actionedBy fields for the access request
     *
     * @param requestId - the access request ID
     * @param approverComments - the comments from the approver
     * @param action - the action taken on the request
     * @param hours - the amount of hours of access in the request
     */
    private void updateRequestDetails(Long requestId, String approverComments, String action, int hours){
        AccessRequest accessRequest = accessRequestRepository.getAccessRequestById(requestId);
        accessRequest.setApproverComments(approverComments);
        accessRequest.setHours(hours);
        GatekeeperUserEntry user = gatekeeperRoleService.getUserProfile();
        accessRequest.setActionedByUserId(user.getUserId());
        accessRequest.setActionedByUserName(user.getName());
        accessRequestRepository.save(accessRequest);
        logger.info("Access Request " + accessRequest.getId() + " was " + action +" by " + user.getName() + " (" + user.getUserId() +"). ");

    }

    /**
     * Approves the Request
     * @param taskId - the activiti task id
     * @param requestId - the AccessRequest object id
     * @param approverComments - The comments from the approver
     * @param hours - the amount of hours of access in the request
     * @return - The updated list of Active Access Requests
     */
    @PreAuthorize("@gatekeeperRoleService.isApprover()")
    public List<ActiveAccessRequestWrapper> approveRequest(String taskId, Long requestId, String approverComments, int hours ) {
        updateRequestDetails(requestId, approverComments, APPROVED, hours);
        handleRequest(gatekeeperRoleService.getUserProfile().getUserId(), taskId, RequestStatus.APPROVAL_GRANTED);
        return getActiveRequests();
    }

    /**
     * Rejects the request
     * @param taskId - the activiti task id
     * @param requestId - the AccessRequest object id
     * @param approverComments - The comments from the approver
     * @return - The updated list of Active Access Requests
     */
    @PreAuthorize("@gatekeeperRoleService.isApprover()")
    public List<ActiveAccessRequestWrapper> rejectRequest(String taskId, Long requestId, String approverComments) {
        updateRequestDetails(requestId, approverComments, REJECTED);
        handleRequest(gatekeeperRoleService.getUserProfile().getUserId(), taskId, RequestStatus.APPROVAL_REJECTED);
        return getActiveRequests();
    }

    private List<CompletedAccessRequestWrapper> getLiveRequests() {

        final Map<Long, Map<String, Object>> historicData = new HashMap<>();
        final String updated = "updated";
        final String status = "requestStatus";

        historyService.createNativeHistoricVariableInstanceQuery()
                .sql("select * from " +
                        "    (select id_, proc_inst_id_, execution_id_, task_id_, text2_ " +
                        "        from act_hi_varinst a where a.name_ = 'accessRequest') a " +
                        "    join (select a.id_, a.proc_inst_id_, a.execution_id_, a.task_id_, c.end_time_ as last_updated_time_, substring(encode(b.bytes_, 'escape'), '\\w+$') as textValue " +
                        "        from act_hi_varinst a " +
                        "            join act_ge_bytearray b on a.bytearray_id_ = b.id_ " +
                        "            join ( select proc_inst_id_, end_time_ from act_hi_actinst where act_id_ = 'grantAccess' " +
                        "               and end_time_ >= ((current_timestamp at time zone 'US/Eastern') - INTERVAL '168 hours')) c on a.proc_inst_id_ = c.proc_inst_id_) b on a.proc_inst_id_ = b.proc_inst_id_ " +
                        "    where textValue like '%GRANTED%'")
                .list()
                .forEach(item -> {
                    Map<String, Object> activitiData = new HashMap<>();
                    activitiData.put(updated, item.getLastUpdatedTime());
                    activitiData.put(status, ((HistoricVariableInstanceEntity) item).getTextValue());
                    historicData.put(Long.valueOf(((HistoricVariableInstanceEntity) item).getTextValue2()), activitiData);
                });

        List<CompletedAccessRequestWrapper> requests = new ArrayList<>();
        accessRequestRepository.getAccessRequestsByIdIn(historicData.keySet()).forEach(accessRequest -> {
            Map<String, Object> data = historicData.get(accessRequest.getId());
            CompletedAccessRequestWrapper completedAccessRequestWrapper = new CompletedAccessRequestWrapper(accessRequest);
            completedAccessRequestWrapper.setUpdated((Date)data.get(updated));
            completedAccessRequestWrapper.setStatus(RequestStatus.valueOf((String)data.get(status)));
            requests.add(completedAccessRequestWrapper);
        });

        // only get requests that got granted.
        return requests.stream()
                .filter(item -> {
                    Calendar expireTime = Calendar.getInstance();
                    expireTime.setTime(item.getUpdated());
                    expireTime.add(Calendar.HOUR, item.getHours());

                    Date currentDate = new Date();
                    boolean isLive = currentDate.before(expireTime.getTime());
                    logger.info("Request " + item.getId() + " is live: " + isLive);
                    return isLive; // the request is live if the difference in time is earlier than updated + request duration
                })
                .collect(Collectors.toList());
    }

    private List<ActiveRequestUserDTO> constructLiveRequestMessageForUsers(List<CompletedAccessRequestWrapper> liveRequests, List<User> users){

        Map<String, ActiveRequestUserDTO> userMap = new HashMap<>();

        users.forEach(user -> {
            final String userId = user.getUserId().substring(3);
            final ActiveRequestUserDTO activeRequestUser = new ActiveRequestUserDTO()
                    .setUserId(userId)
                    .setGkUserId(user.getUserId())
                    .setEmail(user.getEmail());

            liveRequests.forEach(liveRequest -> {
                if(liveRequest.getUsers().stream()
                        .anyMatch(requestUser -> requestUser.getUserId().equals(user.getUserId()))){
                    logger.info(user.getUserId() + " Is part of request " + liveRequest.getId() + " adding the instances to this user's object");
                    liveRequest.getInstances()
                            .forEach(instance -> addRequestData(activeRequestUser.getActiveAccess(), liveRequest.getId(), instance));
                }
            });

            userMap.put(userId, activeRequestUser);
        });

        return new ArrayList<>(userMap.values());
    }


    private List<ActiveRequestUserDTO> addExpiredRequest(List<ActiveRequestUserDTO> activeRequestUserList, AccessRequest expiredRequest) {
        activeRequestUserList.forEach(activeRequestUser ->
                expiredRequest.getInstances()
                        .forEach(instance -> addRequestData(activeRequestUser.getExpiredAccess(), expiredRequest.getId(), instance)));

        return activeRequestUserList;
    }

    /**
     * Cancels the request
     * @param taskId - the activiti task id
     * @param requestId - the AccessRequest object ID
     * @return - The list of active access requests
     */
    public List<ActiveAccessRequestWrapper> cancelRequest(String taskId, Long requestId){
        updateRequestDetails(requestId, "The Request was canceled", CANCELED);
        handleRequest(gatekeeperRoleService.getUserProfile().getUserId(), taskId, RequestStatus.CANCELED);
        return getActiveRequests();
    }

    private List<? extends AccessRequestWrapper> filterResults(List<? extends AccessRequestWrapper> results) {
        return results.stream().filter(AccessRequestWrapper -> gatekeeperRoleService.getRole().equals(GatekeeperRole.APPROVER)
                || gatekeeperRoleService.getRole().equals(GatekeeperRole.AUDITOR)
                || gatekeeperRoleService.getUserProfile().getUserId().equalsIgnoreCase(AccessRequestWrapper.getRequestorId()))
                .collect(Collectors.toList());
    }

    private UserInstancesDTO addRequestData(UserInstancesDTO instanceDetail, Long requestId, AWSInstance awsInstance) {

        List<ActiveAccessRequestDTO> linux = instanceDetail.getLinux();
        List<ActiveAccessRequestDTO> windows = instanceDetail.getWindows();

        if(awsInstance.getPlatform().equals("Linux")){
            linux.add(new ActiveAccessRequestDTO(requestId.toString(), awsInstance.getName(), awsInstance.getIp()));
        } else if (awsInstance.getPlatform().equals("Windows")) {
            windows.add(new ActiveAccessRequestDTO(requestId.toString(), awsInstance.getName(), awsInstance.getIp()));
        }

        return new UserInstancesDTO(linux, windows);
    }
}
