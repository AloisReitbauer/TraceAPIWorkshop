package com.dynatrace.oneagent.sdk.api.enums;

/*
 * ============================================================================================================
 * This API of the Dynatrace SDK is a DRAFT. It's not guaranteed that the final API will look exactly the same.
 * The implementation in this state is *non-functional* It's only exposed for demo-purposes.
 * ============================================================================================================
 */

public enum DatabaseVendor {

	APACHE_HIVE("ApacheHive"),
	CLOUDSCAPE("Cloudscape"),
	HSQLDB("HSQLDB"),
	PROGRESS("Progress"),
	MAXDB("MaxDB"),
	HANADB("HanaDB"),
	INGRES("Ingres"),
	FIRST_SQL("FirstSQL"),
	ENTERPRISE_DB("EnterpriseDB"),
	CACHE("Cache"),
	ADABAS("Adabas"),
	FIREBIRD("Firebird"),
	DB2("DB2"),
	DERBY_CLIENT("Derby Client"),
	DERBY_EMBEDDED("Derby Embedded"),
	FILEMAKER("Filemaker"),
	INFORMIX("Informix"),
	INSTANT_DB("InstantDb"),
	INTERBASE("Interbase"),
	MYSQL("MySQL"),
	MARIADB("MariaDB"),
	NETEZZA("Netezza"),
	ORACLE("Oracle"),
	PERVASIVE("Pervasive"),
	POINTBASE("Pointbase"),
	POSTGRESQL("PostgreSQL"),
	SQLSERVER("SQL Server"),
	SQLITE("sqlite"),
	SYBASE("Sybase"),
	TERADATA("Teradata"),
	VERTICA("Vertica"),
	CASSANDRA("Cassandra"),
	H2("H2"),
	COLDFUSION_IMQ("ColdFusion IMQ"),
	REDSHIFT("Amazon Redshift");
	
	private String vendorName;
	
	private DatabaseVendor(String vendorName) {
		this.vendorName = vendorName;
	}
	
	public String getVendorName() {
		return vendorName;
	}

	@Override
	public String toString() {
		return vendorName;
	}
}
