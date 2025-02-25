/*
 * Copyright 2022. Gatekeeper Contributors
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
 *
 */

package org.finra.gatekeeper.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix="gatekeeper")
public class GatekeeperProperties {

    public AuthenticationProperties getAuth() {
        return auth;
    }

    public GatekeeperProperties setAuth(AuthenticationProperties auth) {
        this.auth = auth;
        return this;
    }

    /**
     * Properties used for authentication
     */
    private AuthenticationProperties auth;

    public static class AuthenticationProperties {
        /**
         * The Group name for the Approvers
         */
        private String approverGroup;
        /**
         * The Group name for the Audit
         */
        private String auditorGroup;
        /**
         * The Group name for the Support team
         */
        private String supportGroup;

        /**
         * Test account name, leave blank for production environments
         */
        private String testAdminAccount;

        /**
         * The header to look for the user's ID (authentication mechanism should inject this header)
         */
        private String userIdHeader;

        /**
         * Ldap Specific settings
         */
        private GatekeeperLdapProperties ldap;

        public String getApproverGroup() {
            return approverGroup;
        }

        public AuthenticationProperties setApproverGroup(String approverGroup) {
            this.approverGroup = approverGroup;
            return this;
        }

        public String getAuditorGroup() {
            return auditorGroup;
        }

        public AuthenticationProperties setAuditorGroup(String auditorGroup) {
            this.auditorGroup = auditorGroup;
            return this;
        }

        public String getSupportGroup() {
            return supportGroup;
        }

        public AuthenticationProperties setSupportGroup(String supportGroup) {
            this.supportGroup = supportGroup;
            return this;
        }

        public String getTestAdminAccount() {
            return testAdminAccount;
        }

        public AuthenticationProperties setTestAdminAccount(String testAdminAccount) {
            this.testAdminAccount = testAdminAccount;
            return this;
        }

        public GatekeeperLdapProperties getLdap() {
            return ldap;
        }

        public AuthenticationProperties setLdap(GatekeeperLdapProperties ldap) {
            this.ldap = ldap;
            return this;
        }

        public String getUserIdHeader() {
            return userIdHeader;
        }

        public AuthenticationProperties setUserIdHeader(String userIdHeader) {
            this.userIdHeader = userIdHeader;
            return this;
        }

        public static class GatekeeperLdapProperties {
            /**
             * The regex pattern at which to capture the name of a particular group
             */
            private String pattern;

            /**
             * The base where all the application groups are definied
             */
            private String groupsBase;

            /**
             * If AWS enabled groups are different from the base groups then set this, otherwise the groupsBase will be used.
             */
            private String awsGroupsBase;

            /**
             * The base where the system will search for users
             */
            private String usersBase;

            /**
             * The base where to search for test users (could be the same as the users base, but sometimes it could be different)
             */
            private String testUsersBase;

            /**
             * The LDAP Attribute for the user's cn
             */
            private String usersCnAttribute;

            /**
             * The LDAP Attribute to get the user's login id
             */

            private String usersIdAttribute;

            /**
             * The LDAP Attribute to get the user's name
             */
            private String usersNameAttribute;

            /**
             * The LDAP Attribute to get the user's email
             */
            private String usersEmailAttribute;

            /**
             * The Distinguished Name for the user
             */
            private String distinguishedName;


            /**
             * The base to search for the restricted AD Groups
             */
            private String restrictedGroupsBase;


            public String getPattern() {
                return pattern;
            }

            public GatekeeperLdapProperties setPattern(String pattern) {
                this.pattern = pattern;
                return this;
            }

            public String getGroupsBase() {
                return groupsBase;
            }

            public GatekeeperLdapProperties setGroupsBase(String groupsBase) {
                this.groupsBase = groupsBase;
                return this;
            }

            public String getAwsGroupsBase() {
                return awsGroupsBase;
            }

            public GatekeeperLdapProperties setAwsGroupsBase(String awsGroupsBase) {
                this.awsGroupsBase = awsGroupsBase;
                return this;
            }

            public String getTestUsersBase() {
                return testUsersBase;
            }

            public GatekeeperLdapProperties setTestUsersBase(String testUsersBase) {
                this.testUsersBase = testUsersBase;
                return this;
            }

            public String getUsersBase() {
                return usersBase;
            }

            public GatekeeperLdapProperties setUsersBase(String usersBase) {
                this.usersBase = usersBase;
                return this;
            }

            public String getUsersCnAttribute() {
                return usersCnAttribute;
            }

            public GatekeeperLdapProperties setUsersCnAttribute(String usersCnAttribute) {
                this.usersCnAttribute = usersCnAttribute;
                return this;
            }

            public String getUsersIdAttribute() {
                return usersIdAttribute;
            }

            public GatekeeperLdapProperties setUsersIdAttribute(String usersIdAttribute) {
                this.usersIdAttribute = usersIdAttribute;
                return this;
            }

            public String getUsersNameAttribute() {
                return usersNameAttribute;
            }

            public GatekeeperLdapProperties setUsersNameAttribute(String usersNameAttribute) {
                this.usersNameAttribute = usersNameAttribute;
                return this;
            }

            public String getUsersEmailAttribute() {
                return usersEmailAttribute;
            }

            public GatekeeperLdapProperties setUsersEmailAttribute(String usersEmailAttribute) {
                this.usersEmailAttribute = usersEmailAttribute;
                return this;
            }

            public String getDistinguishedName() {
                return distinguishedName;
            }

            public GatekeeperLdapProperties setDistinguishedName(String distinguishedName) {
                this.distinguishedName = distinguishedName;
                return this;
            }

            public String getRestrictedGroupsBase() {
                return restrictedGroupsBase;
            }

            public GatekeeperLdapProperties setRestrictedGroupsBase(String restrictedGroupsBase) {
                this.restrictedGroupsBase = restrictedGroupsBase;
                return this;
            }
        }
    }

    public Map<String, SmtpConfig> getSmtp() {
        return smtp;
    }

    public GatekeeperProperties setSmtp(Map<String, SmtpConfig> smtp) {
        this.smtp = smtp;
        return this;
    }

    private Map<String, SmtpConfig> smtp;

    public static class SmtpConfig{
        /**
         * The SMTP server for this environment
         */
        private String server;

        public String getServer() {
            return server;
        }

        public SmtpConfig setServer(String server) {
            this.server = server;
            return this;
        }

    }

    /**
     * Gatekeeper RDS DB Connection Settings (Not for the database where requests are stored)
     */
    private GatekeeperDbProperties db;

    public static class GatekeeperDbProperties{
        private String gkUser;
        private String gkCredentialProvider;
        private String gkPass;
        private String assumeMinServerVersion;
        private String ssl;
        private Map<String, String> supportedDbs;
        private PostgresDbProperties postgres;
        private MySqlDbProperties mysql;
        private DocumentDbProperties documentdb;

        public static class PostgresDbProperties{
            private Boolean ssl;
            private Integer connectTimeout;
            private String sslMode;
            private String sslCert;

            public Boolean getSsl() {
                return ssl;
            }

            public PostgresDbProperties setSsl(Boolean ssl) {
                this.ssl = ssl;
                return this;
            }

            public Integer getConnectTimeout() {
                return connectTimeout;
            }

            public PostgresDbProperties setConnectTimeout(Integer connectTimeout) {
                this.connectTimeout = connectTimeout;
                return this;
            }

            public String getSslMode() {
                return sslMode;
            }

            public PostgresDbProperties setSslMode(String sslMode) {
                this.sslMode = sslMode;
                return this;
            }

            public String getSslCert() {
                return sslCert;
            }

            public PostgresDbProperties setSslCert(String sslCert) {
                this.sslCert = sslCert;
                return this;
            }
        }

        public static class MySqlDbProperties {
            private String ssl;

            public String getSsl() {
                return ssl;
            }

            public MySqlDbProperties setSsl(String ssl) {
                this.ssl = ssl;
                return this;
            }
        }

        public static class DocumentDbProperties {
            private Boolean ssl;
            private String sslCert;
            private String replicaSet;
            private String readPreference;
            private Boolean retryWrites;

            public Boolean getSsl() {
                return ssl;
            }

            public DocumentDbProperties setSsl(Boolean ssl) {
                this.ssl = ssl;
                return this;
            }

            public String getSslCert() {
                return sslCert;
            }

            public DocumentDbProperties setSslCert(String sslCert) {
                this.sslCert = sslCert;
                return this;
            }

            public String getReplicaSet() {
                return replicaSet;
            }

            public DocumentDbProperties setReplicaSet(String replicaSet) {
                this.replicaSet = replicaSet;
                return this;
            }

            public String getReadPreference() {
                return readPreference;
            }

            public DocumentDbProperties setReadPreference(String readPreference) {
                this.readPreference = readPreference;
                return this;
            }

            public Boolean getRetryWrites() {
                return retryWrites;
            }

            public DocumentDbProperties setRetryWrites(Boolean retryWrites) {
                this.retryWrites = retryWrites;
                return this;
            }
        }

        public String getGkUser() {
            return gkUser;
        }

        public GatekeeperDbProperties setGkUser(String gkUser) {
            this.gkUser = gkUser;
            return this;
        }

        public String getGkPass() {
            return gkPass;
        }

        public GatekeeperDbProperties setGkPass(String gkPass) {
            this.gkPass = gkPass;
            return this;
        }

        public String getAssumeMinServerVersion() {
            return assumeMinServerVersion;
        }

        public GatekeeperDbProperties setAssumeMinServerVersion(String assumeMinServerVersion) {
            this.assumeMinServerVersion = assumeMinServerVersion;
            return this;
        }

        public String getSsl() {
            return ssl;
        }

        public GatekeeperDbProperties setSsl(String ssl) {
            this.ssl = ssl;
            return this;
        }

        public Map<String, String> getSupportedDbs() {
            return supportedDbs;
        }

        public GatekeeperDbProperties setSupportedDbs(Map<String, String> supportedDbs) {
            this.supportedDbs = supportedDbs;
            return this;
        }

        public PostgresDbProperties getPostgres() {
            return postgres;
        }

        public GatekeeperDbProperties setPostgres(PostgresDbProperties postgres) {
            this.postgres = postgres;
            return this;
        }

        public MySqlDbProperties getMysql() {
            return mysql;
        }

        public GatekeeperDbProperties setMysql(MySqlDbProperties mysql) {
            this.mysql = mysql;
            return this;
        }

        public DocumentDbProperties getDocumentdb() {
            return documentdb;
        }

        public GatekeeperDbProperties setDocumentdb(DocumentDbProperties documentdb) {
            this.documentdb = documentdb;
            return this;
        }

        public String getGkCredentialProvider() {
            return gkCredentialProvider;
        }

        public GatekeeperDbProperties setGkCredentialProvider(String gkCredentialProvider) {
            this.gkCredentialProvider = gkCredentialProvider;
            return this;
        }
    }

    public GatekeeperDbProperties getDb() {
        return db;
    }

    public GatekeeperProperties setDb(GatekeeperDbProperties gatekeeperDbProperties) {
        this.db = gatekeeperDbProperties;
        return this;
    }



    /**
     * Gatekeeper Lambda Connection Settings
     */
    private GatekeeperLambdaProperties lambda;

    public static class GatekeeperLambdaProperties{
        private Map<String, String> functions;

        public Map<String, String> getFunctions() {
            return functions;
        }

        public void setFunctions(Map<String, String> functions) {
            this.functions = functions;
        }


    }

    public GatekeeperLambdaProperties getLambda() {
        return lambda;
    }

    public void setLambda(GatekeeperLambdaProperties lambda) {
        this.lambda = lambda;
    }

    /**
     * API where AWS account info is provided
     */
    private String accountInfoEndpoint;

    /**
     * The Security Group to check on each RDS instance
     */
    private String requiredSecurityGroups;

    /**
     * The Tag to determine which application a resource belongs to.
     */

    private String appIdentityTag;


    public String getAccountInfoEndpoint() {
        return accountInfoEndpoint;
    }

    public GatekeeperProperties setAccountInfoEndpoint(String accountInfoEndpoint) {
        this.accountInfoEndpoint = accountInfoEndpoint;
        return this;
    }

    public String getRequiredSecurityGroups() {
        return requiredSecurityGroups;
    }

    public GatekeeperProperties setRequiredSecurityGroups(String requiredSecurityGroups) {
        this.requiredSecurityGroups = requiredSecurityGroups;
        return this;
    }

    public String getAppIdentityTag() {
        return appIdentityTag;
    }

    public GatekeeperProperties setAppIdentityTag(String appIdentityTag) {
        this.appIdentityTag = appIdentityTag;
        return this;
    }
}
