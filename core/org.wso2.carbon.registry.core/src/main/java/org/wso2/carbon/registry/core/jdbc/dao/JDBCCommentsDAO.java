/*
 *  Copyright (c) 2005-2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.registry.core.jdbc.dao;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.registry.core.*;
import org.wso2.carbon.registry.core.dao.CommentsDAO;
import org.wso2.carbon.registry.core.dao.ResourceDAO;
import org.wso2.carbon.registry.core.dataaccess.DAOManager;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.jdbc.DatabaseConstants;
import org.wso2.carbon.registry.core.jdbc.dataaccess.JDBCDatabaseTransaction;
import org.wso2.carbon.registry.core.jdbc.dataobjects.CommentDO;
import org.wso2.carbon.registry.core.session.CurrentSession;
import org.wso2.carbon.registry.core.utils.RegistryUtils;
import org.wso2.carbon.utils.DBUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An implementation of the {@link CommentsDAO} to store comments on a JDBC-based database.
 */
public class JDBCCommentsDAO implements CommentsDAO {

    private static final Log log = LogFactory.getLog(JDBCCommentsDAO.class);
    private ResourceDAO resourceDAO;

    protected static final Object ADD_COMMENT_LOCK = new Object();

    /**
     * Default constructor
     *
     * @param daoManager instance of the data access object manager.
     */
    public JDBCCommentsDAO(DAOManager daoManager) {
        this.resourceDAO = daoManager.getResourceDAO();
    }

    public int addComment(ResourceImpl resource, String userID, Comment comment)
            throws RegistryException {
        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();
        PreparedStatement ps1 = null, ps2 = null, ps3 = null;
        int commentId = -1;
        try {
            String sql1 = "INSERT INTO REG_COMMENT (REG_COMMENT_TEXT," +
                    "REG_USER_ID, REG_COMMENTED_TIME, REG_TENANT_ID) VALUES (?, ?, ?, ?)";
            String sql2 = "SELECT MAX(REG_ID) FROM REG_COMMENT";
            String sql3 = "INSERT INTO REG_RESOURCE_COMMENT (REG_COMMENT_ID, REG_PATH_ID, " +
                    "REG_RESOURCE_NAME, REG_TENANT_ID) VALUES (?, ?, ?, ?)";
            String dbProductName = conn.getMetaData().getDatabaseProductName();
            boolean returnsGeneratedKeys = DBUtils.canReturnGeneratedKeys(dbProductName);
            if (returnsGeneratedKeys) {
                ps1 = conn.prepareStatement(sql1, new String[]{DBUtils
                        .getConvertedAutoGeneratedColumnName(dbProductName,
                                DatabaseConstants.ID_FIELD)});
            } else {
                ps1 = conn.prepareStatement(sql1);
            }
            ps3 = conn.prepareStatement(sql3);

            // prepare to execute query2 for the comments
            ps1.setString(1, comment.getText());
            ps1.setString(2, userID);

            long now = System.currentTimeMillis();
            ps1.setTimestamp(3, new Timestamp(now));
            ps1.setInt(4, CurrentSession.getTenantId());
            ResultSet resultSet1;
            if (returnsGeneratedKeys) {
                ps1.executeUpdate();
                resultSet1 = ps1.getGeneratedKeys();
            } else {
                synchronized (ADD_COMMENT_LOCK) {
                    ps1.executeUpdate();
                    ps2 = conn.prepareStatement(sql2);
                    resultSet1 = ps2.executeQuery();
                }
            }
            try {
                if (resultSet1.next()) {
                    // setting the RES_COMMENTS_ID
                    commentId = resultSet1.getInt(1);

                    ps3.setInt(1, commentId);
                    ps3.setInt(2, resource.getPathID());
                    ps3.setString(3, resource.getName());
                    ps3.setInt(4, CurrentSession.getTenantId());
                    ps3.executeUpdate();
                }
            } finally {
                if (resultSet1 != null) {
                    resultSet1.close();
                }
            }

        } catch (SQLException e) {

            String msg = "Failed to add comments to the resource " +
                    resource.getPath() + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (ps1 != null) {
                        ps1.close();
                    }
                } finally {
                    try {
                        if (ps2 != null) {
                            ps2.close();
                        }
                    } finally {
                        if (ps3 != null) {
                            ps3.close();
                        }
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
        return commentId;
    }

    public void addComments(ResourceImpl resource, CommentDO[] commentDOs)
            throws RegistryException {
        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();
        PreparedStatement ps1 = null, ps2 = null, ps3 = null;
        try {
            String sql1 = "INSERT INTO REG_COMMENT (REG_COMMENT_TEXT," +
                    "REG_USER_ID, REG_COMMENTED_TIME, REG_TENANT_ID) VALUES (?, ?, ?, ?)";
            String sql2 = "SELECT MAX(REG_ID) FROM REG_COMMENT";
            String sql3 = "INSERT INTO REG_RESOURCE_COMMENT (REG_COMMENT_ID, " +
                    "REG_PATH_ID, REG_RESOURCE_NAME, REG_TENANT_ID) VALUES (?, ?, ?, ?)";

            String dbProductName = conn.getMetaData().getDatabaseProductName();
            boolean returnsGeneratedKeys = DBUtils.canReturnGeneratedKeys(dbProductName);
            if (returnsGeneratedKeys) {
                ps1 = conn.prepareStatement(sql1, new String[]{DBUtils
                        .getConvertedAutoGeneratedColumnName(dbProductName,
                                DatabaseConstants.ID_FIELD)});
            } else {
                ps1 = conn.prepareStatement(sql1);
            }
            ps3 = conn.prepareStatement(sql3);

            for (CommentDO comment : commentDOs) {
                // prepare to execute query2 for the comments
                ps1.setString(1, comment.getCommentText());
                ps1.setString(2, comment.getCommentedUser());

                long now = System.currentTimeMillis();
                ps1.setTimestamp(3, new Timestamp(now));
                ps1.setInt(4, CurrentSession.getTenantId());
                ResultSet resultSet1;
                if (returnsGeneratedKeys) {
                    ps1.executeUpdate();
                    resultSet1 = ps1.getGeneratedKeys();
                } else {
                    synchronized (ADD_COMMENT_LOCK) {
                        ps1.executeUpdate();
                        ps2 = conn.prepareStatement(sql2);
                        resultSet1 = ps2.executeQuery();
                    }
                }
                try {
                    if (resultSet1.next()) {
                        // setting the RES_COMMENTS_ID
                        int commentId = resultSet1.getInt(1);

                        ps3.setInt(1, commentId);
                        ps3.setInt(2, resource.getPathID());
                        ps3.setString(3, resource.getName());
                        ps3.setInt(4, CurrentSession.getTenantId());

                        ps3.executeUpdate();
                        ps3.clearParameters();
                    }
                } finally {
                    if (resultSet1 != null) {
                        resultSet1.close();
                    }
                }
                ps3.clearParameters();
            }

        } catch (SQLException e) {

            String msg = "Failed to add comments to the resource " +
                    resource.getPath() + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (ps1 != null) {
                        ps1.close();
                    }
                } finally {
                    try {
                        if (ps2 != null) {
                            ps2.close();
                        }
                    } finally {
                        if (ps3 != null) {
                            ps3.close();
                        }
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }

    public void copyComments(ResourceImpl sourceResource, ResourceImpl targetResource)
            throws RegistryException {
        if (sourceResource == null || targetResource == null || sourceResource.getPath() == null
                || sourceResource.getPath().equals(targetResource.getPath())) {
            // no special copying needed
            return;
        }
        Comment[] comments = getComments(sourceResource);
        CommentDO[] commentDOs = new CommentDO[comments.length];
        for (int i = 0; i < comments.length; i++) {
            CommentDO commentDO = new CommentDO();
            commentDO.setCommentedUser(comments[i].getUser());
            commentDO.setCommentText(comments[i].getText());
            commentDOs[i] = commentDO;
        }
        addComments(targetResource, commentDOs);
    }

    public void updateComment(long commentId, String text) throws RegistryException {

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();
        PreparedStatement s = null;
        try {
            String sql = "UPDATE REG_COMMENT SET REG_COMMENT_TEXT=?,REG_COMMENTED_TIME=? WHERE " +
                    "REG_ID=? AND REG_TENANT_ID=?";
            long now = System.currentTimeMillis();
            s = conn.prepareStatement(sql);
            s.setString(1, text);
            s.setTimestamp(2, new Timestamp(now));
            s.setLong(3, commentId);
            s.setInt(4, CurrentSession.getTenantId());
            s.executeUpdate();

        } catch (SQLException e) {

            String msg = "Failed to update the comment with ID " + commentId +
                    " with text " + text + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                if (s != null) {
                    s.close();
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }

    public void deleteComment(long commentId) throws RegistryException {

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();

        PreparedStatement s = null;
        PreparedStatement s1 = null;
        try {
            String sql =
                    "DELETE FROM REG_RESOURCE_COMMENT WHERE REG_COMMENT_ID=? AND REG_TENANT_ID=?";

            s = conn.prepareStatement(sql);
            s.setLong(1, commentId);
            s.setInt(2, CurrentSession.getTenantId());
            s.executeUpdate();

            sql = "DELETE FROM REG_COMMENT WHERE REG_ID=? AND REG_TENANT_ID=?";

            s1 = conn.prepareStatement(sql);
            s1.setLong(1, commentId);
            s1.setInt(2, CurrentSession.getTenantId());
            s1.executeUpdate();

        } catch (SQLException e) {

            String msg = "Failed to delete the comment with ID " +
                    commentId + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (s != null) {
                        s.close();
                    }
                } finally {
                    if (s1 != null) {
                        s1.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }

    public void removeComments(ResourceImpl resource) throws RegistryException {

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();

        PreparedStatement ps1 = null, ps2 = null;
        String path = resource.getPath();
        Comment[] comments = getComments(resource);
        try {
            String sql1 =
                    "DELETE FROM REG_RESOURCE_COMMENT WHERE REG_COMMENT_ID = ? AND REG_TENANT_ID=?";
            ps1 = conn.prepareStatement(sql1);

            String sql2 = "DELETE FROM REG_COMMENT WHERE REG_ID = ? AND REG_TENANT_ID=?";
            ps2 = conn.prepareStatement(sql2);

            for (Comment comment : comments) {
                ps1.setLong(1, comment.getCommentID());
                ps1.setInt(2, CurrentSession.getTenantId());
                ps2.setLong(1, comment.getCommentID());
                ps2.setInt(2, CurrentSession.getTenantId());

                ps1.addBatch();
                ps2.addBatch();
            }

            if (comments.length > 0) {
                try {
                    ps1.executeBatch();
                    ps2.executeBatch();
                } catch (SQLException e) {
                    ps1.clearBatch();
                    ps2.clearBatch();
                    // the exception will be handled in the next catch block
                    throw e;
                }
            }

        } catch (SQLException e) {

            String msg = "Failed to get comments on resource " + path + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (ps1 != null) {
                        ps1.close();
                    }
                } finally {
                    if (ps2 != null) {
                        ps2.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }

    public Comment getComment(long commentID, String resourcePath) throws RegistryException {

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();
        PreparedStatement s = null;
        ResultSet result = null;
        try {
            String sql =
                    "SELECT C.REG_ID, C.REG_COMMENT_TEXT, C.REG_USER_ID, C.REG_COMMENTED_TIME " +
                            "FROM REG_COMMENT C WHERE C.REG_ID=? AND C.REG_TENANT_ID=?";

            s = conn.prepareStatement(sql);
            s.setLong(1, commentID);
            s.setInt(2, CurrentSession.getTenantId());

            result = s.executeQuery();

            Comment comment = null;
            if (result.next()) {
                String commentPath = resourcePath + RegistryConstants.URL_SEPARATOR + "comments:" +
                        result.getInt(DatabaseConstants.ID_FIELD);

                comment = new Comment();
                comment.setText(result.getString(DatabaseConstants.COMMENT_TEXT_FIELD));
                comment.setUser(result.getString(DatabaseConstants.USER_ID_FIELD));
                comment.setCreatedTime(result.getTimestamp(DatabaseConstants.COMMENTED_TIME_FIELD));
                comment.setResourcePath(resourcePath);
                comment.setPath(commentPath);
                comment.setCommentPath(commentPath);
                comment.setParentPath(resourcePath + RegistryConstants.URL_SEPARATOR + "comments");
            }
            return comment;

        } catch (SQLException e) {

            String msg = "Failed to get comment with ID " + commentID + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (result != null) {
                        result.close();
                    }
                } finally {
                    if (s != null) {
                        s.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }

    public Comment[] getComments(ResourceImpl resource) throws RegistryException {

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();

        List<Comment> commentList = new ArrayList<Comment>();
        PreparedStatement s = null;
        ResultSet results = null;
        String path = resource.getPath();
        try {
            String sql;
            if (resource instanceof CollectionImpl) {
                sql = "SELECT C.REG_ID, C.REG_COMMENT_TEXT, C.REG_USER_ID, C.REG_COMMENTED_TIME " +
                        "FROM REG_COMMENT C, REG_RESOURCE_COMMENT RC " +
                        "WHERE C.REG_ID=RC.REG_COMMENT_ID AND " +
                        "RC.REG_PATH_ID = ? AND RC.REG_RESOURCE_NAME IS NULL AND " +
                        "C.REG_TENANT_ID=? AND RC.REG_TENANT_ID=?";
                s = conn.prepareStatement(sql);
                s.setInt(1, resource.getPathID());
                s.setInt(2, CurrentSession.getTenantId());
                s.setInt(3, CurrentSession.getTenantId());
            } else {
                sql = "SELECT C.REG_ID, C.REG_COMMENT_TEXT, C.REG_USER_ID, C.REG_COMMENTED_TIME " +
                        "FROM REG_COMMENT C, REG_RESOURCE_COMMENT RC " +
                        "WHERE C.REG_ID=RC.REG_COMMENT_ID AND " +
                        "RC.REG_PATH_ID = ? AND RC.REG_RESOURCE_NAME = ? AND " +
                        "C.REG_TENANT_ID=? AND RC.REG_TENANT_ID=?";
                s = conn.prepareStatement(sql);
                s.setInt(1, resource.getPathID());
                s.setString(2, resource.getName());
                s.setInt(3, CurrentSession.getTenantId());
                s.setInt(4, CurrentSession.getTenantId());
            }

            results = s.executeQuery();
            while (results.next()) {
                Comment comment = new Comment();
                comment.setText(results.getString(DatabaseConstants.COMMENT_TEXT_FIELD));
                comment.setUser(results.getString(DatabaseConstants.USER_ID_FIELD));
                comment.setCreatedTime(
                        results.getTimestamp(DatabaseConstants.COMMENTED_TIME_FIELD));
                comment.setResourcePath(path);
                String commentPath = path + RegistryConstants.URL_SEPARATOR + "comments:" +
                        results.getInt(DatabaseConstants.ID_FIELD);
                comment.setPath(commentPath);
                comment.setCommentPath(commentPath);
                comment.setParentPath(path + RegistryConstants.URL_SEPARATOR + "comments");
                comment.setCommentID(results.getLong(DatabaseConstants.ID_FIELD));
                commentList.add(comment);
            }

        } catch (SQLException e) {

            String msg = "Failed to get comments on resource " + path + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (results != null) {
                        results.close();
                    }
                } finally {
                    if (s != null) {
                        s.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }

        return commentList.toArray(new Comment[commentList.size()]);
    }

    /**
     * Method to get resource paths of comments.
     *
     * @param commentIDs the comment id.
     * @param conn       the connection to use.
     *
     * @return array of resource paths.
     * @throws RegistryException if an error occurs.
     */
    public String[] getResourcePathsOfComments(Long[] commentIDs, Connection conn)
            throws RegistryException {

        if (commentIDs.length == 0) {
            return new String[0];
        }
        StringBuffer sqlBuf = new StringBuffer();
        sqlBuf.append(
                "SELECT DISTINCT RC.REG_COMMENT_ID, RC.REG_PATH_ID, RC.REG_RESOURCE_NAME FROM " +
                        "REG_RESOURCE_COMMENT RC WHERE (");
        for (int i = 0; i < commentIDs.length; i++) {
            if (i > 0) {
                sqlBuf.append(" OR ");
            }
            sqlBuf.append("RC.REG_COMMENT_ID=?");
        }
        sqlBuf.append(") AND RC.REG_TENANT_ID=?");

        Map<Long, String> commentPathMap = new HashMap<Long, String>();
        List<String> commentPathList = new ArrayList<String>();

        ResultSet results = null;
        PreparedStatement s = null;
        try {
            s = conn.prepareStatement(sqlBuf.toString());
            int i;
            for (i = 0; i < commentIDs.length; i++) {
                s.setLong(i + 1, commentIDs[i]);
            }
            s.setInt(i + 1, CurrentSession.getTenantId());

            results = s.executeQuery();
            while (results.next()) {
                long commentID = results.getLong(DatabaseConstants.COMMENT_ID_FIELD);

                int pathId = results.getInt(DatabaseConstants.PATH_ID_FIELD);
                String resourceName = results.getString(DatabaseConstants.RESOURCE_NAME_FIELD);
                String path = resourceDAO.getPath(pathId, resourceName, true);
                if (path != null) {
                    String commentPath =
                            path + RegistryConstants.URL_SEPARATOR + "comments:" + commentID;
                    commentPathMap.put(commentID, commentPath);
                }
            }
            for (Long commentID : commentIDs) {
                commentPathList.add(commentPathMap.get(commentID));
            }
        } catch (SQLException e) {


            String msg = "Failed to get the resource for the set of comment ids."
                    + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (results != null) {
                        results.close();
                    }
                } finally {
                    if (s != null) {
                        s.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }

        return commentPathList.toArray(new String[commentPathList.size()]);
    }

    public ResourceImpl getResourceWithMinimumData(String path) throws RegistryException {
        return RegistryUtils.getResourceWithMinimumData(path, resourceDAO, false);
    }

    public void moveComments(ResourceIDImpl source, ResourceIDImpl target)
            throws RegistryException {
        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();
        PreparedStatement ps = null;
        try {
            if (source.isCollection()) {
                String sql = "UPDATE REG_RESOURCE_COMMENT SET REG_PATH_ID=? WHERE " +
                        "REG_PATH_ID=? AND REG_RESOURCE_NAME IS NULL AND REG_TENANT_ID=?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, target.getPathID());
                ps.setInt(2, source.getPathID());
                ps.setInt(3, CurrentSession.getTenantId());
                ps.executeUpdate();
            } else {
                String sql =
                        "UPDATE REG_RESOURCE_COMMENT SET REG_PATH_ID=?, REG_RESOURCE_NAME=? " +
                                "WHERE REG_PATH_ID=? AND REG_RESOURCE_NAME=? AND REG_TENANT_ID=?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, target.getPathID());
                ps.setString(2, target.getName());
                ps.setInt(3, source.getPathID());
                ps.setString(4, source.getName());
                ps.setInt(5, CurrentSession.getTenantId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            String msg = "Failed to move comments from  " + source.getPath() +
                    " to " + target.getPath() + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }

    public void moveCommentPaths(ResourceIDImpl source, ResourceIDImpl target)
            throws RegistryException {
        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();
        PreparedStatement ps = null;
        try {
            String sql =
                    "UPDATE REG_RESOURCE_COMMENT SET REG_PATH_ID=? WHERE REG_PATH_ID=? " +
                            "AND REG_TENANT_ID=?";
            ps = conn.prepareStatement(sql);
            ps.setInt(1, target.getPathID());
            ps.setInt(2, source.getPathID());
            ps.setInt(3, CurrentSession.getTenantId());
            ps.executeUpdate();
        } catch (SQLException e) {
            String msg = "Failed to move comment paths from  " + source.getPath() +
                    " to " + target.getPath() + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }
    
    @Override
    public void removeVersionComments(long regVersion)
    		throws RegistryException {
        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();

        PreparedStatement ps1 = null, ps2 = null;

        List<Long> commentIds = getCommentIds(regVersion);
        if (commentIds == null) {
            return;
        }
        try {

            String sql = "DELETE FROM REG_RESOURCE_COMMENT WHERE REG_COMMENT_ID= ? AND REG_TENANT_ID=?";
            ps1 = conn.prepareStatement(sql);

            sql = "DELETE FROM REG_COMMENT WHERE REG_ID= ? AND REG_TENANT_ID=?";
            ps2 = conn.prepareStatement(sql);

            for (long l : commentIds) {
                ps1.setLong(1, l);
                ps1.setInt(2, CurrentSession.getTenantId());
                ps2.setLong(1, l);
                ps2.setInt(2, CurrentSession.getTenantId());
                ps1.addBatch();
                ps2.addBatch();
            }

            if (commentIds.size() > 0) {
                try {
                    ps1.executeBatch();
                    ps2.executeBatch();
                } catch (SQLException e) {
                    ps1.clearBatch();
                    ps2.clearBatch();
                    // the exception will be handled in the next catch block
                    throw e;
                }
            }

        } catch (SQLException e) {

            String msg =
                    "Failed to remove comments for the version: " + regVersion + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (ps1 != null) {
                        ps1.close();
                    }
                } finally {
                    if (ps2 != null) {
                        ps2.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }   	
    }    

    private List<Long> getCommentIds(long regVersionId) throws RegistryException {

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();
        PreparedStatement ps = null;
        ResultSet results = null;
        List<Long> idList = new ArrayList<Long>();
    	
        try {        	       	
        		
        		
        	String sql = "SELECT C.REG_ID FROM REG_COMMENT C, REG_RESOURCE_COMMENT RC WHERE " +
        			"RC.REG_VERSION = ? AND RC.REG_TENANT_ID = ? AND RC.REG_TENANT_ID=C.REG_TENANT_ID " +
        			"AND RC.REG_COMMENT_ID=C.REG_ID";    	
        	
        	ps = conn.prepareStatement(sql);       		
        	        		
        	ps.setLong(1, regVersionId);        		
        	ps.setInt(2, CurrentSession.getTenantId());        		
        	results = ps.executeQuery();
        	
        	while(results.next()){
        		idList.add(results.getLong(1));       		
        	}        	
        } catch (Exception ex) {
            String msg = "Failed to retreive the Comments with the REG_VERSION: " +
                    regVersionId + ". " + ex.getMessage();                
            log.error(msg, ex);
            throw new RegistryException(msg, ex);
        } finally {
            try {
                try {
                    if (results != null) {
                        results.close();
                    }
                } finally {
                    if (ps != null) {
                        ps.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }        
    	return idList;
    }
}
