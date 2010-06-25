/**
 ** Copyright (c) 2009 Memorial Sloan-Kettering Cancer Center (MSKCC)
 ** and University of Toronto (UofT).
 **
 ** This is free software; you can redistribute it and/or modify it
 ** under the terms of the GNU Lesser General Public License as published
 ** by the Free Software Foundation; either version 2.1 of the License, or
 ** any later version.
 **
 ** This library is distributed in the hope that it will be useful, but
 ** WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
 ** MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
 ** documentation provided hereunder is on an "as is" basis, and
 ** both UofT and MSKCC have no obligations to provide maintenance, 
 ** support, updates, enhancements or modifications.  In no event shall
 ** UofT or MSKCC be liable to any party for direct, indirect, special,
 ** incidental or consequential damages, including lost profits, arising
 ** out of the use of this software and its documentation, even if
 ** UofT or MSKCC have been advised of the possibility of such damage.  
 ** See the GNU Lesser General Public License for more details.
 **
 ** You should have received a copy of the GNU Lesser General Public License
 ** along with this software; if not, write to the Free Software Foundation,
 ** Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA;
 ** or find it at http://www.fsf.org/ or http://www.gnu.org.
 **/
package cpath.dao.internal;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import cpath.config.CPathSettings;
import cpath.dao.DataServices;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * This is a fantastic (crazy) factory that 
 * helps create any cPath database schema, 
 * and it is also a dynamic data source factory!
 * 
 * Note: it is MySQL-specific.
 * 
 * @author rodche
 */
public class DataServicesFactoryBean implements DataServices, BeanNameAware, FactoryBean<DataSource> {
    // log
    private static Log log = LogFactory.getLog(DataServicesFactoryBean.class);

	// ref to some db props - set via spring
	private String dbUser;
	@Value("${user}")
	public void setDbUser(String dbUser) { this.dbUser = dbUser; }
	public String getDbUser() { return dbUser; }

	private String dbPassword;
	@Value("${password}")
	public void setDbPassword(String dbPassword) { this.dbPassword = dbPassword; }
	public String getDbPassword() { return dbPassword; }

	private String dbDriver;
	@Value("${driver}")
	public void setDbDriver(String dbDriver) { this.dbDriver = dbDriver; }
	public String getDbDriver() { return dbDriver; }

	private String dbConnection;
	@Value("${connection}")
	public void setDbConnection(String dbConnection) { this.dbConnection = dbConnection; }
	public String getDbConnection() { return dbConnection; }
	
	private JdbcTemplate jdbcTemplate;
	public JdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}

    private String beanName;
    
    /**
     * This sort of map allows for a DataSource dynamically 
     * created at runtime (e.g., by #{@link cpath.importer.internal.PremergeImpl}
     * persistPathway method) to be associated with a key (e.g.,"myId") 
     * and then used by any internal spring context within the same thread 
     * loaded from a xml configuration that contains the following -
     * <bean id="myId" class="cpath.dao.internal.DataServicesFactoryBean"/>
     */	
    private static ThreadLocal<Map<String, DataSource>> beansByName =
        new ThreadLocal<Map<String, DataSource>>() {
            @Override
            protected Map<String, DataSource> initialValue() {
                return new HashMap<String, DataSource>(1);
            }
        };
    
    public static Map<String, DataSource> getDataSourceMap() {
    	return beansByName.get();
    }
        

    @Override
    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    
    @Override
    public DataSource getObject() {
    	DataSource ds = getDataSourceMap().get(beanName);
    	if(ds == null) {
    		// create new one
    		ds = getDataSource(beanName);
    		getDataSourceMap().put(beanName, ds);
    	}
        return ds; 
    }

    @Override
    public Class<?> getObjectType() {
        return getObject().getClass();
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
	
	
	/**
	 * Default Constructor.
	 */
	public DataServicesFactoryBean() {}
	

	@PostConstruct public void init() {
		if (dbConnection == null) {
			throw new IllegalArgumentException("The database connection string is required");
		}
		if (dbDriver == null) {
			throw new IllegalArgumentException("The database driver class name is required");
		}
		if (dbUser == null) {
			throw new IllegalArgumentException("The path to the test data set is required");
		}
		if (dbPassword == null) {
			throw new IllegalArgumentException("The path to the test data set is required");
		}
	}
    
    public boolean createDatabase(final String db, final boolean drop) {
		boolean toReturn = true;

		// create simple JdbcTemplate if necessary
		if (jdbcTemplate == null) {
			DataSource dataSource = getDataSource("mysql"); // works for MySQL
			jdbcTemplate = new JdbcTemplate(dataSource);
		}

		try {
			// drop if desired
			if (drop)
				jdbcTemplate.execute("DROP DATABASE IF EXISTS " + db);

			// create
			jdbcTemplate.execute("CREATE DATABASE " + db);
		}
		catch (DataAccessException e) {
			e.printStackTrace();
			toReturn = false;
		}

		// outta here
		return toReturn;
	}


	/**
	 * Factory-method that get a new data source using instance
	 * variables: driver, connection, user, password, and
	 * parameter database name.
	 * 
	 * (non-Javadoc)
	 * @see cpath.dao.DataServices#getDataSource(java.lang.String)
	 */
	public DataSource getDataSource(String databaseName) {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName(dbDriver);
		dataSource.setUrl(dbConnection + databaseName);
		dataSource.setUsername(dbUser);
		dataSource.setPassword(dbPassword);
		return dataSource;
	}
	
	
	/* *** static methods *** */
	
	/**
	 * Static factory method that creates any DataSource.
	 * 
	 * @param dbUser
	 * @param dbPassword
	 * @param dbDriver
	 * @param dbUrl
	 * @return
	 */
	public static DataSource getDataSource(String dbUser, String dbPassword,
			String dbDriver, String dbUrl) {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName(dbDriver);
		dataSource.setUrl(dbUrl);
		dataSource.setUsername(dbUser);
		dataSource.setPassword(dbPassword);
		return dataSource;
	}	

	
	/**
	 * Creates all the (pre-defined) 
	 * "production" databases and tables.
	 */
	public static void createDatabases() {
		createSchema(CPathSettings.MAIN_DB);
		createSchema(CPathSettings.METADATA_DB);
		createSchema(CPathSettings.MOLECULES_DB);
		createSchema(CPathSettings.PROTEINS_DB);
	}
	
	
	/**
	 * Creates all the (pre-defined) 
	 * test databases and tables.
	 */
	public static void createTestDatabases() {
		createSchema(CPathSettings.MAIN_DB + CPathSettings.TEST_SUFFIX);
		createSchema(CPathSettings.METADATA_DB + CPathSettings.TEST_SUFFIX);
		createSchema(CPathSettings.MOLECULES_DB + CPathSettings.TEST_SUFFIX);
		createSchema(CPathSettings.PROTEINS_DB + CPathSettings.TEST_SUFFIX);
	}
	
	/**
	 * Drops, creates database schema.
	 * 
	 * This is called by a special context
	 * that {@link #createSchema(String)} loads.
	 * 
	 * @param user
	 * @param passwd
	 * @param driver
	 * @param conn
	 * @param dbName
	 */
	static void createDatabase(String user, String passwd, 
			String driver, String conn, String dbName) 
	{
		// drop, create database
		DataSource adminDataSource = getDataSource(user, passwd, driver, conn);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(adminDataSource);
		// drop
		jdbcTemplate.execute("DROP DATABASE IF EXISTS " + dbName);
		// create
		jdbcTemplate.execute("CREATE DATABASE " + dbName);
		
		DataSource create = getDataSource(user, passwd, driver, conn+dbName);
		// save the new data source in the static map
		getDataSourceMap().put("createdDb", create);
	}
	
	
	/**
	 * Fantastic way to create a database schema!
	 * 
	 * This implicitly calls 
	 * {@link #createDatabase(String, String, String, String, String)} 
	 * method.
	 * 
	 * @param dbName - db name to initialize
	 */
	public static void createSchema(String dbName) {
		// set the system property (new db name)
		System.setProperty("cpath2.db.name", dbName);
		// load the context that depends on the above property -
		ApplicationContext ctx = new ClassPathXmlApplicationContext(
				"classpath:internalContext-createSchema.xml");
		// all done!
	}
}
