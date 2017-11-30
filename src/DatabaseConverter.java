/**
 * Created by Kajal Kukreja on 20-09-2017.
 * This code is used for converting charset and collation of database, table and column for making the application Universal compliant.
 * For exact database conversion, please run this program only once on a particular database.
 * If you get any error while program is running, kindly revert to old database and try again.
 * Do not run it again without reverting the corrupted database.
 */

import com.mysql.jdbc.JDBC4PreparedStatement;
import com.mysql.jdbc.StringUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

public class DatabaseConverter {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConverter.class.getName());;

    private static List<KeyDetail> primaryKeyIndexes = new ArrayList<>();
    private static List<KeyDetail> uniqueKeyIndexes = new ArrayList<>();
    private static List<KeyDetail> foreignKeyIndexes = new ArrayList<>();
    private static List<KeyDetail> otherIndexes = new ArrayList<>();

    private static StringBuilder allQueries = new StringBuilder();
    private static StringBuilder requiredQueries = new StringBuilder();

    private static String databaseName = null;
    private static String newCharset = null;
    private static String newCollation = null;

    //loading properties file
    private static Properties properties = null;

    static {
        try {
            properties = new Properties();
            String propertiesFile = "application.properties";
            InputStream inputStream = DatabaseConverter.class.getClassLoader().getResourceAsStream(propertiesFile);
            properties.load(inputStream);

            //initialize static variables
            databaseName = properties.getProperty("databaseName");
            newCharset =  properties.getProperty("newCharset");
            newCollation =  properties.getProperty("newCollation");

        } catch(Exception e) {
            LOGGER.fatal("Exception " + e + " has occurred while loading properties file!");
            e.printStackTrace();
            System.exit(0);
        }
    }

    public static void main(String[] args) {

        Connection informationSchemaConnection = null;
        Connection myDBConnection = null;

        try {
            informationSchemaConnection = getConnection("INFORMATION_SCHEMA");

            myDBConnection = getConnection(databaseName);

            //disable foreign key checks because we have dropped foreign keys temporarily and if this check is enabled alter query will fail
            setForeignKeyChecks(myDBConnection, 0);

            //find out all the indexes applied on this database
            collectAllIndexes(informationSchemaConnection);
            printIndexes();

            //remove all the indexes on this database
            dropAllIndexes(informationSchemaConnection, myDBConnection);

            boolean changedDB = changeDatabaseCharsetAndCollation(informationSchemaConnection, newCharset, newCollation);

            if (changedDB) {
                boolean changedColumns = changeColumnCharsetAndCollation(informationSchemaConnection, myDBConnection, newCharset, newCollation);

                if (changedColumns) {
                    boolean changedTables = changeTableCharsetAndCollation(informationSchemaConnection, myDBConnection, newCharset, newCollation);

                    if (changedTables) {

                        //recreating all indexes on this database
                        createAllIndexes(myDBConnection);

                        LOGGER.info("\n\nAll indexes have been recreated, please verify them as below -\n");

                        //clearing all the existing indexes from all collections
                        primaryKeyIndexes = new ArrayList<>();
                        uniqueKeyIndexes = new ArrayList<>();
                        foreignKeyIndexes = new ArrayList<>();
                        otherIndexes = new ArrayList<>();

                        //again collect all indexes and verify them
                        collectAllIndexes(informationSchemaConnection);
                        printIndexes();

                        LOGGER.info("\nAll good!");

                        //enable foreign key checks and strict mode
                        setForeignKeyChecks(myDBConnection, 1);

                        //LOGGER.info("Printing all queries\n\n");
                        //LOGGER.info(String.valueOf(allQueries));

                        //storing all queries in sql file
                        String allQueriesFilename = databaseName + "-all-queries.sql";
                        Path allQueriesFile = Paths.get(allQueriesFilename);
                        byte allQueriesData[] = StringUtils.getBytes(String.valueOf(allQueries));
                        Files.write(allQueriesFile, allQueriesData);
                        LOGGER.info("\nStored all queries in " + allQueriesFilename + " file.");

                        //storing all required queries in sql file
                        String requiredQueriesFilename = databaseName + "-required-queries.sql";
                        Path requiredQueriesFile = Paths.get(requiredQueriesFilename);
                        byte requiredQueriesData[] = StringUtils.getBytes(String.valueOf(requiredQueries));
                        Files.write(requiredQueriesFile, requiredQueriesData);
                        LOGGER.info("Stored all queries in " + requiredQueriesFilename + " file.");
                    }
                }
            }
        } catch (ClassNotFoundException | SQLException | IOException e) {
            LOGGER.fatal("Exception : " + e);
            e.printStackTrace();
        } finally {
            try {
                //close the connections
                if (informationSchemaConnection != null) {
                    closeConnection(informationSchemaConnection);
                }
                if (myDBConnection != null) {
                    closeConnection(myDBConnection);
                }
            } catch (SQLException s) {
                LOGGER.fatal("Exception : " + s);
                s.printStackTrace();
            }
        }
    }

    public static Connection getConnection(String databaseName) throws ClassNotFoundException, SQLException {
        Connection connection = null;
        if (connection == null) {
            String username = properties.getProperty("username");
            String password = properties.getProperty("password");
            String host = properties.getProperty("host");
            String port = properties.getProperty("port");

            Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + databaseName, username, password);
            LOGGER.info("Connection to database " + databaseName + " is successful!");
        }
        return connection;
    }

    public static void closeConnection(Connection connection) throws SQLException {
        connection.close();
        LOGGER.info("Connection closed!");
    }

    public static void setForeignKeyChecks(Connection myDBConnection, int checkValue) throws SQLException {
        PreparedStatement preparedStatement = myDBConnection.prepareStatement("SET foreign_key_checks = ?;");
        preparedStatement.setInt(1, checkValue);

        allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql() + "\n");
        requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql() + "\n");

        preparedStatement.execute();
        if (checkValue == 0) {
            LOGGER.info("Foreign key check disabled for database " + databaseName + "!");
        }
        else if(checkValue == 1) {
            LOGGER.info("Foreign key check enabled for database " + databaseName + "!");
        }
    }

    public static ResultSet getTables(Connection informationSchemaConnection) throws SQLException {
        PreparedStatement preparedStatement = informationSchemaConnection.prepareStatement("SELECT TABLE_NAME FROM TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE';");
        preparedStatement.setString(1, databaseName);

        allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("FROM TABLES", "FROM INFORMATION_SCHEMA.TABLES") + "\n");
        //requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("FROM TABLES", "FROM INFORMATION_SCHEMA.TABLES") + "\n");

        return preparedStatement.executeQuery();
    }

    public static ResultSet getDefaultCharsetAndCollation(Connection informationSchemaConnection) throws SQLException {
        PreparedStatement preparedStatement = informationSchemaConnection.prepareStatement("SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM SCHEMATA WHERE SCHEMA_NAME = ?;");
        preparedStatement.setString(1, databaseName);

        allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("FROM SCHEMATA", "FROM INFORMATION_SCHEMA.SCHEMATA") + "\n");
        //requiredQueries.append(((JDBC4PreparedStatement) preparedStatement).asSql().replace("FROM SCHEMATA", "FROM INFORMATION_SCHEMA.SCHEMATA") + "\n");

        return preparedStatement.executeQuery();
    }

    public static boolean changeDatabaseCharsetAndCollation(Connection informationSchemaConnection, String newCharset, String newCollation) throws SQLException {

        ResultSet resultSet = getDefaultCharsetAndCollation(informationSchemaConnection);
        if (resultSet.next()) {
            String defaultCharset = resultSet.getString("DEFAULT_CHARACTER_SET_NAME");
            String defaultCollation = resultSet.getString("DEFAULT_COLLATION_NAME");
            if (defaultCharset.equals(newCharset) && defaultCollation.equals(newCollation)) {
                LOGGER.info("\nDefault Charset of database : " + defaultCharset);
                LOGGER.info("Default Collation of database : " + defaultCollation + "\n");
                return true;
            }
            else {
                /*
                We need to append database name directly to the query because
                adding it using setString() adds quotes to database name which is not acceptable in this mysql query
                */
                PreparedStatement preparedStatement = informationSchemaConnection.prepareStatement("ALTER DATABASE " + databaseName + " CHARACTER SET = ? COLLATE = ?;");
                preparedStatement.setString(1, newCharset);
                preparedStatement.setString(2, newCollation);

                allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql() + "\n");
                requiredQueries.append(((JDBC4PreparedStatement) preparedStatement).asSql() + "\n");

                int result = preparedStatement.executeUpdate();
                if (result > 0) {
                    LOGGER.info("Database charset and collation modified!");

                    ResultSet newResultSet = getDefaultCharsetAndCollation(informationSchemaConnection);
                    if (newResultSet.next()) {
                        newCharset = newResultSet.getString("DEFAULT_CHARACTER_SET_NAME");
                        newCollation = newResultSet.getString("DEFAULT_COLLATION_NAME");
                        LOGGER.info("\nDefault Charset of database : " + newCharset);
                        LOGGER.info("Default Collation of database : " + newCollation + "\n");
                    }
                    return true;
                } else {
                    LOGGER.info("Failed to modify database charset and collation!");
                }
            }
        }
        return false;
    }


    public static boolean changeTableCharsetAndCollation(Connection informationSchemaConnection, Connection myDBConnection, String newCharset, String newCollation) throws SQLException {
        PreparedStatement preparedStatement = informationSchemaConnection.prepareStatement("SELECT TABLE_NAME FROM TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' AND (TABLE_COLLATION IS NULL || TABLE_COLLATION != ?);");
        preparedStatement.setString(1, databaseName);
        preparedStatement.setString(2, newCollation);

        allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("FROM TABLES", "FROM INFORMATION_SCHEMA.TABLES") + "\n");
        //requiredQueries.append(((JDBC4PreparedStatement) preparedStatement).asSql().replace("FROM TABLES", "FROM INFORMATION_SCHEMA.TABLES") + "\n");

        ResultSet resultSet =  preparedStatement.executeQuery();
        while (resultSet.next()) {
            String tableName = resultSet.getString("TABLE_NAME");
            /*
            We need to append table name directly to the query because
            adding it using setString() adds quotes to table name which is not acceptable in this mysql query
            */

            //First, convert charset and collation of tables who already have data to be converted
            preparedStatement = myDBConnection.prepareStatement("ALTER TABLE " + tableName + " CONVERT TO CHARACTER SET ? COLLATE ?;");
            preparedStatement.setString(1, newCharset);
            preparedStatement.setString(2, newCollation);
            LOGGER.info("Converting table " + tableName);

            allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + tableName, "ALTER TABLE " + databaseName + "." + tableName) + "\n");
            requiredQueries.append(((JDBC4PreparedStatement) preparedStatement).asSql().replace("ALTER TABLE " + tableName, "ALTER TABLE " + databaseName + "." + tableName) + "\n");

            preparedStatement.executeUpdate();

            //Now, change default charset and collation of table
            preparedStatement = myDBConnection.prepareStatement("ALTER TABLE " + tableName + " CHARACTER SET ? COLLATE ?;");
            preparedStatement.setString(1, newCharset);
            preparedStatement.setString(2, newCollation);

            allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + tableName, "ALTER TABLE " + databaseName + "." + tableName)  + "\n");
            requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + tableName, "ALTER TABLE " + databaseName + "." + tableName) + "\n");

            preparedStatement.executeUpdate();
        }

        int noOfTablesWithOtherCharsetAndEncoding = getNoOfTablesWithOtherCharsetAndEncoding(informationSchemaConnection, newCollation);

        if (noOfTablesWithOtherCharsetAndEncoding == 0) {
            LOGGER.info("All tables have been converted successfully!");
            return true;
        }
        return false;
    }

    public static int getNoOfTablesWithOtherCharsetAndEncoding(Connection informationSchemaConnection, String newCollation) throws SQLException {
        PreparedStatement preparedStatement = informationSchemaConnection.prepareStatement("SELECT COUNT(*) FROM TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' AND TABLE_COLLATION IS NOT NULL AND TABLE_COLLATION != ?;");
        preparedStatement.setString(1, databaseName);
        preparedStatement.setString(2, newCollation);

        allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("FROM TABLES", "FROM INFORMATION_SCHEMA.TABLES") + "\n");
        //requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("FROM TABLES", "FROM INFORMATION_SCHEMA.TABLES") + "\n");

        ResultSet resultSet = preparedStatement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getInt(1);
        }
        return 0;
    }

    public static boolean changeColumnCharsetAndCollation(Connection informationSchemaConnection, Connection myDBConnection, String newCharset, String newCollation) throws SQLException {
        //views don't have any collation but columns under view do have collation so we need to consider that scenario also
        ResultSet resultSet = getTables(informationSchemaConnection);

        while (resultSet.next()) {
            String tableName = resultSet.getString("TABLE_NAME");

            // Fetching all columns who have some collation_name
            PreparedStatement preparedStatement = informationSchemaConnection.prepareStatement("SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLLATION_NAME IS NOT NULL AND COLLATION_NAME != ?;");
            preparedStatement.setString(1, databaseName);
            preparedStatement.setString(2, tableName);
            preparedStatement.setString(3, newCollation);

            allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("FROM COLUMNS", "FROM INFORMATION_SCHEMA.COLUMNS") + "\n");
            //requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("FROM COLUMNS", "FROM INFORMATION_SCHEMA.COLUMNS") + "\n");

            ResultSet columnResultSet = preparedStatement.executeQuery();
            while (columnResultSet.next()) {
                /*
                We need to append table name and column names directly to the query because
                adding it using setString() adds quotes to them which is not acceptable in this mysql query
                */

                //ALTER TABLE table_name CHANGE column_name column_name VARCHAR(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
                String columnName = columnResultSet.getString("COLUMN_NAME");
                //data type returns varchar without size
                String dataType = columnResultSet.getString("DATA_TYPE");
                //column type returns size of varchar
                String columnType = columnResultSet.getString("COLUMN_TYPE");
                //check if column can contain null or not
                String isNullable = columnResultSet.getString("IS_NULLABLE");
                //get the default value
                String columnDefault = columnResultSet.getString("COLUMN_DEFAULT");

                String notNull = "";
                String defaultValue = "";
                if (isNullable.equals("NO")) {
                    notNull = " NOT NULL";
                }
                if (columnDefault != null) {
                    defaultValue = " DEFAULT '" + columnDefault + "'";
                }
                preparedStatement = myDBConnection.prepareStatement("ALTER TABLE " + tableName + " CHANGE " + columnName + " " + columnName + " " + columnType + " CHARACTER SET ? COLLATE ?" + notNull + defaultValue + ";");
                preparedStatement.setString(1, newCharset);
                preparedStatement.setString(2, newCollation);
                LOGGER.info("Converting Table : " + tableName + "\t\tColumn : " + columnName);

                allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + tableName, "ALTER TABLE " + databaseName + "." + tableName) + "\n");
                requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + tableName, "ALTER TABLE " + databaseName + "." + tableName) + "\n");

                preparedStatement.executeUpdate();
            }
        }

        int noOfColumnsWithOtherCharsetAndEncoding = getNoOfColumnsWithOtherCharsetAndEncoding(informationSchemaConnection, newCollation);

        if (noOfColumnsWithOtherCharsetAndEncoding == 0) {
            LOGGER.info("All columns have been converted successfully!");
            return true;
        }
        return false;
    }

    public static int getNoOfColumnsWithOtherCharsetAndEncoding(Connection informationSchemaConnection, String newCollation) throws SQLException {
        PreparedStatement preparedStatement = informationSchemaConnection.prepareStatement("SELECT COUNT(COLUMN_NAME) FROM COLUMNS WHERE TABLE_SCHEMA = ? AND COLLATION_NAME IS NOT NULL AND COLLATION_NAME != ?;");
        preparedStatement.setString(1, databaseName);
        preparedStatement.setString(2, newCollation);

        allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("FROM COLUMNS", "FROM INFORMATION_SCHEMA.COLUMNS") + "\n");
        //requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("FROM COLUMNS", "FROM INFORMATION_SCHEMA.COLUMNS") + "\n");

        ResultSet resultSet = preparedStatement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getInt(1);
        }
        return 0;
    }

    public static void collectAllIndexes(Connection informationSchemaConnection) throws SQLException {
        PreparedStatement preparedStatement = informationSchemaConnection.prepareStatement("SELECT TABLE_NAME, COLUMN_NAME, COLUMN_KEY, COLLATION_NAME, CHARACTER_MAXIMUM_LENGTH FROM COLUMNS WHERE TABLE_SCHEMA = ?;");
        preparedStatement.setString(1, databaseName);

        allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("FROM COLUMNS", "FROM INFORMATION_SCHEMA.COLUMNS") + "\n");
        //requiredQueries.append(((JDBC4PreparedStatement) preparedStatement).asSql().replace("FROM COLUMNS", "FROM INFORMATION_SCHEMA.COLUMNS") + "\n");

        ResultSet resultSet = preparedStatement.executeQuery();
        while (resultSet.next()) {
            String tableName = resultSet.getString("TABLE_NAME");
            String columnName = resultSet.getString("COLUMN_NAME");
            String columnKey = resultSet.getString("COLUMN_KEY");
            String collationName = resultSet.getString("COLLATION_NAME");
            boolean hasCollation = false;
            if (collationName != null && !collationName.isEmpty()) {
                hasCollation = true;
            }

            String characterMaxLength = resultSet.getString("CHARACTER_MAXIMUM_LENGTH");

            /*
            Store all the indexes of this column in otherIndexes and then remove the index from otherIndex if it is primary key, unique key or foreign key
            and add it to respective list
            */
            preparedStatement = informationSchemaConnection.prepareStatement("SHOW INDEX FROM " + databaseName + "." + tableName + " WHERE COLUMN_NAME = ?;");
            preparedStatement.setString(1, columnName);

            allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql() + "\n");
            //requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql() + "\n");

            ResultSet indexResultSet = preparedStatement.executeQuery();
            while (indexResultSet.next()) {
                String keyName = indexResultSet.getString("KEY_NAME");
                otherIndexes.add(new KeyDetail(tableName, columnName, keyName, null, null, indexResultSet.getString("SEQ_IN_INDEX"), characterMaxLength, hasCollation));
            }


            //now search for unique keys, primary keys and foreign keys
            preparedStatement = informationSchemaConnection.prepareStatement("SELECT CONSTRAINT_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME, ORDINAL_POSITION FROM KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?;");
            preparedStatement.setString(1, databaseName);
            preparedStatement.setString(2, tableName);
            preparedStatement.setString(3, columnName);

            allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("FROM KEY_COLUMN_USAGE", "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE") + "\n");
            //requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("FROM KEY_COLUMN_USAGE", "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE") + "\n");

            ResultSet keyUsageResultSet = preparedStatement.executeQuery();
            while (keyUsageResultSet.next()) {
                String constraintName = keyUsageResultSet.getString("CONSTRAINT_NAME");
                String referencedTableName = keyUsageResultSet.getString("REFERENCED_TABLE_NAME");
                String referencedColumnName = keyUsageResultSet.getString("REFERENCED_COLUMN_NAME");
                String ordinalPosition = keyUsageResultSet.getString("ORDINAL_POSITION");

                if (columnKey.equals("PRI")) {
                    primaryKeyIndexes.add(new KeyDetail(tableName, columnName, constraintName, referencedTableName, referencedColumnName, ordinalPosition, characterMaxLength, hasCollation));
                    otherIndexes.remove(new KeyDetail(tableName, columnName, constraintName, null, null, ordinalPosition, characterMaxLength, hasCollation));
                }
                //there are scenarios where unique key is also a composite key
                //mul means it can be foreign key or composite unique key, for composite key, 1 col has mul n another one doesn't have it
                else if (columnKey.equals("UNI") || columnKey.equals("MUL")) {
                    if (referencedTableName != null) {
                        foreignKeyIndexes.add(new KeyDetail(tableName, columnName, constraintName, referencedTableName, referencedColumnName, ordinalPosition, characterMaxLength, hasCollation));
                        otherIndexes.remove(new KeyDetail(tableName, columnName, constraintName, null, null, ordinalPosition, characterMaxLength, hasCollation));
                    } else {
                        uniqueKeyIndexes.add(new KeyDetail(tableName, columnName, constraintName, referencedTableName, referencedColumnName, ordinalPosition, characterMaxLength, hasCollation));
                        otherIndexes.remove(new KeyDetail(tableName, columnName, constraintName, null, null, ordinalPosition, characterMaxLength, hasCollation));
                    }
                } else {
                    preparedStatement = informationSchemaConnection.prepareStatement("SHOW INDEX FROM " + databaseName + "." + tableName + " WHERE KEY_NAME = ? AND COLUMN_NAME = ?;");
                    preparedStatement.setString(1, constraintName);
                    preparedStatement.setString(2, columnName);

                    allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql() + "\n");
                    //requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql() + "\n");

                    ResultSet uniqueIndexResultSet = preparedStatement.executeQuery();
                    while (uniqueIndexResultSet.next()) {
                        uniqueKeyIndexes.add(new KeyDetail(tableName, columnName, constraintName, referencedTableName, referencedColumnName, ordinalPosition, characterMaxLength, hasCollation));
                        otherIndexes.remove(new KeyDetail(tableName, columnName, constraintName, null, null, ordinalPosition, characterMaxLength, hasCollation));
                    }
                }
            }
        }
    }

    public static void printIndexes() {
        int totalIndexes = primaryKeyIndexes.size() + uniqueKeyIndexes.size() + foreignKeyIndexes.size() + otherIndexes.size();

        //for composite keys, count is incremented based on number of columns involved in creating key
        LOGGER.info("\nPrimary key index count : " + primaryKeyIndexes.size());
        LOGGER.info("Unique key index count : " + uniqueKeyIndexes.size());
        LOGGER.info("Foreign key index count : " + foreignKeyIndexes.size());
        LOGGER.info("Other index count : " + otherIndexes.size());
        LOGGER.info("Total index count : " + totalIndexes);

        //ignoring primary keys for now
        /*
        if (primaryKeyIndexes.size() > 0) {
            LOGGER.info("\n============ PRIMARY KEYS ====================\n");
        }
        for (KeyDetail index : primaryKeyIndexes) {
            LOGGER.info(index.toString());
        }*/

        if (uniqueKeyIndexes.size() > 0) {
            LOGGER.info("\n================ UNIQUE KEYS ================\n");
        }
        for (KeyDetail index : uniqueKeyIndexes) {
            LOGGER.info(index.toString());
        }

        if (foreignKeyIndexes.size() > 0) {
            LOGGER.info("\n============= FOREIGN KEYS ===================\n");
        }
        for (KeyDetail index : foreignKeyIndexes) {
            LOGGER.info(index.toString());
        }

        if (otherIndexes.size() > 0) {
            LOGGER.info("\n============= OTHER INDEXES ===================\n");
        }
        for (KeyDetail index : otherIndexes){
            LOGGER.info(index.toString());
        }
    }

    public static boolean indexExists(Connection informationSchemaConnection, String tableName, String constraintName) throws SQLException {
        PreparedStatement preparedStatement = informationSchemaConnection.prepareStatement("SELECT CONSTRAINT_NAME FROM KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?;");
        preparedStatement.setString(1, databaseName);
        preparedStatement.setString(2, tableName);
        preparedStatement.setString(3, constraintName);

        allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("FROM KEY_COLUMN_USAGE", "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE") + "\n");
        //requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("FROM KEY_COLUMN_USAGE", "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE") + "\n");

        ResultSet resultSet = preparedStatement.executeQuery();
        if (resultSet.next()) {
            return true;
        }
        return false;
    }

    public static void dropAllIndexes(Connection informationSchemaConnection, Connection myDBConnection) throws SQLException {
        //problem is only for indexes whose collation is to be changed ie. varchar, text, collation
        dropForeignKeyIndexes(informationSchemaConnection, myDBConnection);
        dropPrimaryIndexes(myDBConnection);
        dropUniqueIndexes(informationSchemaConnection, myDBConnection);
        dropOtherIndexes(myDBConnection);
    }

    public static void dropForeignKeyIndexes(Connection informationSchemaConnection, Connection myDBConnection) throws SQLException {
        if (foreignKeyIndexes.size() > 0) {
            LOGGER.info("\nDropping all foreign key indexes\n");
        }
        Iterator<KeyDetail> iterator = foreignKeyIndexes.iterator();
        while (iterator.hasNext()) {
            KeyDetail index = iterator.next();
            PreparedStatement preparedStatement = null;
            if (indexExists(informationSchemaConnection, index.getTableName(), index.getConstraintName())) {
                //ALTER TABLE mytable DROP FOREIGN KEY mytable_ibfk_1 ;
                preparedStatement = myDBConnection.prepareStatement("ALTER TABLE " + index.getTableName() + " DROP FOREIGN KEY " + index.getConstraintName() + ";");

                allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + index.getTableName(), "ALTER TABLE " + databaseName + "." + index.getTableName()) + "\n");
                requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + index.getTableName(), "ALTER TABLE " + databaseName + "." + index.getTableName()) + "\n");

                preparedStatement.executeUpdate();
                LOGGER.info("Dropped foreign key " + index.getConstraintName());
            }

            preparedStatement = informationSchemaConnection.prepareStatement("SHOW INDEX FROM " + databaseName + "." + index.getTableName() + " WHERE KEY_NAME = ? AND COLUMN_NAME = ?;");
            preparedStatement.setString(1, index.getConstraintName());
            preparedStatement.setString(2, index.getColumnName());

            allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql() + "\n");
            //requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql() + "\n");

            ResultSet foreignKeyIndexResultSet = preparedStatement.executeQuery();

            if (foreignKeyIndexResultSet.next()) {
                preparedStatement = myDBConnection.prepareStatement("DROP INDEX " + index.getConstraintName() + " ON " + index.getTableName() + ";");

                allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ON " + index.getTableName(), "ON " + databaseName + "." + index.getTableName()) + "\n");
                requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ON " + index.getTableName(), "ON " + databaseName + "." + index.getTableName()) + "\n");

                preparedStatement.executeUpdate();
                LOGGER.info("Dropped foreign key index " + index.getConstraintName());
            }
        }
    }

    public static void dropPrimaryIndexes(Connection myDBConnection) throws SQLException {

    }

    public static void dropUniqueIndexes(Connection informationSchemaConnection, Connection myDBConnection) throws SQLException {
        if (uniqueKeyIndexes.size() > 0) {
            LOGGER.info("\nDropping all unique indexes\n");
        }
        Iterator<KeyDetail> iterator = uniqueKeyIndexes.iterator();
        while (iterator.hasNext()) {
            KeyDetail index = iterator.next();
            if (indexExists(informationSchemaConnection, index.getTableName(), index.getConstraintName())) {
                //drop index indexname on tablename;
                PreparedStatement preparedStatement = myDBConnection.prepareStatement("ALTER TABLE " + index.getTableName() + " DROP INDEX " + index.getConstraintName() + ";");

                allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + index.getTableName(), "ALTER TABLE " + databaseName + "." + index.getTableName()) + "\n");
                requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + index.getTableName(), "ALTER TABLE " + databaseName + "." + index.getTableName()) + "\n");

                preparedStatement.executeUpdate();
                LOGGER.info("Dropped unique key " + index.getConstraintName());
            }
        }
    }

    public static void dropOtherIndexes(Connection myDBConnection) throws SQLException {
        if (otherIndexes.size() > 0) {
            LOGGER.info("\nDropping all other indexes\n");
        }
        Iterator<KeyDetail> iterator = otherIndexes.iterator();
        while (iterator.hasNext()) {
            KeyDetail index = iterator.next();
            //drop index indexname on tablename;
            PreparedStatement preparedStatement = myDBConnection.prepareStatement("ALTER TABLE " + index.getTableName() + " DROP INDEX " + index.getConstraintName() + ";");

            allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + index.getTableName(), "ALTER TABLE " + databaseName + "." + index.getTableName()) + "\n");
            requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + index.getTableName(), "ALTER TABLE " + databaseName + "." + index.getTableName()) + "\n");

            preparedStatement.executeUpdate();
            LOGGER.info("Dropped other index " + index.getConstraintName());
        }
    }

    public static void createAllIndexes(Connection myDBConnection) throws SQLException {
        createPrimaryIndexes(myDBConnection);
        createUniqueIndexes(myDBConnection);
        createOtherIndexes(myDBConnection);
        createForeignKeyIndexes(myDBConnection);
    }

    public static void createPrimaryIndexes(Connection myDBConnection) throws SQLException {

    }

    public static void createUniqueIndexes(Connection myDBConnection) throws SQLException {
        if (uniqueKeyIndexes.size() > 0) {
            LOGGER.info("\nCreating all unique key indexes\n");
        }
        HashMap<HashMap<String, String>, String> uniqueKeyColumns = getAllPairs(uniqueKeyIndexes);

        for (Map.Entry<HashMap<String, String>, String> entry : uniqueKeyColumns.entrySet()) {
            String tableName = "";
            String constraintName = "";
            HashMap<String, String> entryKey = entry.getKey();
            String columns = entry.getValue().replace("[", "").replace("]", "");

            for (Map.Entry<String, String> key : entryKey.entrySet()) {
                tableName = key.getKey();
                constraintName = key.getValue();
            }
            //LOGGER.info("TableName : " + tableName + "\tConstraintName : " + constraintName + "\tColumns : " + columns);
            //ALTER TABLE gtldtest.application_status ADD UNIQUE pbapplictinstts_pplicatinid(id);
            PreparedStatement preparedStatement = myDBConnection.prepareStatement("ALTER TABLE " + tableName +" ADD UNIQUE " + constraintName + "(" + columns + ");");

            allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + tableName, "ALTER TABLE " + databaseName + "." + tableName) + "\n");
            requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + tableName, "ALTER TABLE " + databaseName + "." + tableName) + "\n");

            preparedStatement.executeUpdate();
            LOGGER.info("Created unique key index " + constraintName);
        }
    }

    public static void createOtherIndexes(Connection myDBConnection) throws SQLException {
        if (otherIndexes.size() > 0) {
            LOGGER.info("\nCreating all other indexes\n");
        }
        HashMap<HashMap<String, String>, String> otherIndexColumns = getAllPairs(otherIndexes);

        for (Map.Entry<HashMap<String, String>, String> entry : otherIndexColumns.entrySet()) {
            String tableName = "";
            String constraintName = "";
            HashMap<String, String> entryKey = entry.getKey();
            String columns = entry.getValue().replace("[", "").replace("]", "");

            for (Map.Entry<String, String> key : entryKey.entrySet()) {
                tableName = key.getKey();
                constraintName = key.getValue();
            }
            //LOGGER.info("TableName : " + tableName + "\tConstraintName : " + constraintName + "\tColumns : " + columns);
            //ALTER TABLE gtldtest.application_status ADD KEY pbapplictinstts_pplicatinid(id);
            PreparedStatement preparedStatement = myDBConnection.prepareStatement("ALTER TABLE " + tableName +" ADD KEY " + constraintName + "(" + columns + ");");

            allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + tableName, "ALTER TABLE " + databaseName + "." + tableName) + "\n");
            requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + tableName, "ALTER TABLE " + databaseName + "." + tableName) + "\n");

            preparedStatement.executeUpdate();
            LOGGER.info("Created other index " + constraintName);
        }
    }

    public static void createForeignKeyIndexes(Connection myDBConnection) throws SQLException {
        if (foreignKeyIndexes.size() > 0) {
            LOGGER.info("\nCreating all foreign key indexes\n");
        }
        Iterator<KeyDetail> iterator = foreignKeyIndexes.iterator();
        while (iterator.hasNext()) {
            KeyDetail index = iterator.next();
            if (index.isHasCollation() && Integer.parseInt(index.getCharacterMaxLength()) >= 191) {
                index.setColumnName(index.getColumnName() + "(191)");
            }
            //ALTER TABLE gtldtest.application_change_log ADD CONSTRAINT FK_log_from_attachment_id FOREIGN KEY (from_attachment_id) REFERENCES gtldtest.gtld_application_attachment(id) ON DELETE CASCADE;
            PreparedStatement preparedStatement = myDBConnection.prepareStatement("ALTER TABLE " + index.getTableName() +" ADD CONSTRAINT " + index.getConstraintName() + " FOREIGN KEY (" + index.getColumnName() + ") REFERENCES " + index.getReferencedTableName() + "(" + index.getReferencedColumnName() + ") ON DELETE CASCADE;");

            allQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + index.getTableName(), "ALTER TABLE " + databaseName + "." + index.getTableName()) + "\n");
            requiredQueries.append(((JDBC4PreparedStatement)preparedStatement).asSql().replace("ALTER TABLE " + index.getTableName(), "ALTER TABLE " + databaseName + "." + index.getTableName()) + "\n");

            preparedStatement.executeUpdate();
            LOGGER.info("Created foreign key index " + index.getConstraintName());
            iterator.remove();
        }
    }

    public static HashMap<HashMap<String, String>, String> getAllPairs(List<KeyDetail> indexes) {

        HashMap<HashMap<String, String>, String> allColumns = new HashMap<>();
        Iterator<KeyDetail> indexesIterator = indexes.iterator();

        while (indexesIterator.hasNext()) {

            KeyDetail key1 = indexesIterator.next();
            //LOGGER.info("\n\nKey1 : " + key1);

            List<KeyDetail> allIndexesMatchingConstraintName = new ArrayList<>();
            Iterator<KeyDetail> iterator2 = indexes.iterator();

            while (iterator2.hasNext()) {

                KeyDetail key2 = iterator2.next();
                //LOGGER.info("Comparing with " + key2);

                if (key1 != null && key2 != null && key1.getTableName().equals(key2.getTableName()) && key1.getConstraintName().equals(key2.getConstraintName())) {
                    allIndexesMatchingConstraintName.add(key2);
                    //iterator2.remove();
                }
            }

            allIndexesMatchingConstraintName.add(key1);

            String[] allColumnsArray = new String[allIndexesMatchingConstraintName.size() - 1];

            Iterator<KeyDetail> allIndexesIterator =  allIndexesMatchingConstraintName.iterator();
            while (allIndexesIterator.hasNext()) {
                KeyDetail index = allIndexesIterator.next();
                String columnName = index.getColumnName();

                /*
                If the column has some collation only then we need to append column name with length 191 otherwise this column cannot be indexed.
                The prefix length 191 represents that utf8mb4 collation will be used for indexing of such columns.
                If there is no collation for column for example for int, date, timestamp such columns then index cannot take prefix 191 and
                exception will be thrown. So, no need to set 191 index length for them.

                We need to check characterMaxLength because only 255 characters are allowed to be indexed.
                After that limit, index cannot store values.
                For utf8mb4, we need to index only 191 characters so indexing won't be done without prefix 191.
                */
                if (index.isHasCollation() && Integer.parseInt(index.getCharacterMaxLength()) >= 191) {
                    columnName += "(191)";
                }
                allColumnsArray[Integer.parseInt(index.getOrdinalPosition()) - 1] = columnName;
                allIndexesIterator.remove();
            }

            HashMap<String, String> hashMapKey = new HashMap<>();
            hashMapKey.put(key1.getTableName(), key1.getConstraintName());
            allColumns.put(hashMapKey, Arrays.toString(allColumnsArray));

            //indexesIterator.remove();
        }

        return allColumns;
    }
}