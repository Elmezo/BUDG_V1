package com.example.budg_v2.dao;

import com.example.budg_v2.model.Product;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.SegmentAccessService;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ProductDAO {
    
    private static final String SELECT_ALL = "SELECT * FROM product WHERE deleteddatetime IS NULL ORDER BY primaryname";
    private static final String SELECT_FOR_DROPDOWN = "SELECT id, primaryname, description, refnumber FROM product WHERE deleteddatetime IS NULL ORDER BY primaryname";
    private static final String SELECT_BY_ID = "SELECT p.*, s.primaryname AS statusName FROM product p " +
            "LEFT JOIN status s ON s.ID = p.status " +
            "WHERE p.id = ?";
    private static final String SELECT_BY_REFNUMBER = "SELECT * FROM product WHERE refnumber = ?";
    private static final String SELECT_BY_REFNUMBER_EXCLUDE_ID = "SELECT * FROM product WHERE refnumber = ? AND id != ?";
    private static final String SEARCH = "SELECT * FROM product WHERE primaryname LIKE ? OR description LIKE ? OR refnumber LIKE ? ORDER BY primaryname";
    private static final String INSERT = "INSERT INTO product (id, primaryname, description, refnumber, longname, parent_id, status, is_public, lifecycle_status, createdatetime, lastupdatedatetime, createdby_id, lastupdate_userid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE product SET primaryname = ?, description = ?, refnumber = ?, longname = ?, parent_id = ?, status = ?, is_public = ?, lifecycle_status = ?, lastupdatedatetime = ?, lastupdate_userid = ? WHERE id = ?";
    private static final String DELETE = "UPDATE product SET lastupdate_userid = ?, lastupdatedatetime = ? WHERE id = ?";

    public List<Product> getAllProducts() throws SQLException {
        return getAllProductsForGuest();
    }

    /**
     * Get all products for guest users (public, Enterprise only, not deleted)
     */
    private List<Product> getAllProductsForGuest() throws SQLException {
        String guestFilter = SegmentAccessService.buildGuestFilterClause("Product", "p", "p.id");
        if (guestFilter == null) {
            return getAllProductsUnfiltered();
        }
        String sql = "SELECT p.* FROM product p WHERE " + guestFilter + " ORDER BY p.primaryname";
        List<Product> products = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                products.add(mapResultSetToProduct(rs));
            }
        }
        return products;
    }

    public List<Product> getAllProductsUnfiltered() throws SQLException {
        List<Product> products = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                products.add(mapResultSetToProduct(rs));
            }
        }
        return products;
    }

    /**
     * Get all products filtered by user's segment access
     */
    public List<Product> getAllProducts(int userId) throws SQLException {
        if (userId <= 0) {
            return getAllProductsForGuest();
        }
        List<Product> allProducts = getAllProductsUnfiltered();
        if (allProducts.isEmpty()) {
            return allProducts;
        }
        
        // Get accessible product IDs for this user (filtered by selected segments)
        List<Integer> allIds = allProducts.stream().map(Product::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Product", allIds);
        
        // Filter to only accessible products
        return allProducts.stream()
                .filter(p -> accessibleIds.contains(p.getId()))
                .collect(Collectors.toList());
    }

    public List<Product> getProductsForDropdown() throws SQLException {
        return getProductsForDropdownForGuest();
    }

    /**
     * Get products for dropdown for guest users (public, Enterprise only, not deleted)
     */
    private List<Product> getProductsForDropdownForGuest() throws SQLException {
        String guestFilter = SegmentAccessService.buildGuestFilterClause("Product", "p", "p.id");
        if (guestFilter == null) {
            return getProductsForDropdownUnfiltered();
        }
        String sql = "SELECT p.id, p.primaryname, p.description, p.refnumber FROM product p WHERE " + guestFilter + " ORDER BY p.primaryname";
        List<Product> products = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Product product = new Product();
                product.setId(rs.getInt("id"));
                product.setPrimaryName(rs.getString("primaryname"));
                product.setDescription(rs.getString("description"));
                product.setRefNumber(rs.getString("refnumber"));
                products.add(product);
            }
        }
        return products;
    }

    private List<Product> getProductsForDropdownUnfiltered() throws SQLException {
        List<Product> products = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_FOR_DROPDOWN);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Product product = new Product();
                product.setId(rs.getInt("id"));
                product.setPrimaryName(rs.getString("primaryname"));
                product.setDescription(rs.getString("description"));
                product.setRefNumber(rs.getString("refnumber"));
                products.add(product);
            }
        }
        return products;
    }

    /**
     * Get products for dropdown filtered by user's segment access
     */
    public List<Product> getProductsForDropdown(int userId) throws SQLException {
        if (userId <= 0) {
            return getProductsForDropdownForGuest();
        }
        List<Product> allProducts = getProductsForDropdownUnfiltered();
        if (allProducts.isEmpty()) {
            return allProducts;
        }
        
        // Get accessible product IDs for this user (filtered by selected segments)
        List<Integer> allIds = allProducts.stream().map(Product::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Product", allIds);
        
        // Filter to only accessible products
        return allProducts.stream()
                .filter(p -> accessibleIds.contains(p.getId()))
                .collect(Collectors.toList());
    }

    public Product getProductById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID)) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToProduct(rs);
                }
            }
        }
        
        return null;
    }

    public List<Product> searchProducts(String searchTerm) throws SQLException {
        List<Product> products = new ArrayList<>();
        String searchPattern = "%" + searchTerm + "%";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SEARCH)) {
            
            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);
            stmt.setString(3, searchPattern);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    products.add(mapResultSetToProduct(rs));
                }
            }
        }
        
        return products;
    }

    public int insertProduct(Product product) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            
            // Generate a unique ID
            int newId = generateNewProductId(conn);
            
            stmt.setInt(1, newId);
            stmt.setString(2, product.getPrimaryName());
            stmt.setString(3, product.getDescription());
            stmt.setString(4, product.getRefNumber());
            stmt.setString(5, product.getLongName());
            stmt.setObject(6, product.getParentId());
            stmt.setObject(7, product.getStatus());
            stmt.setObject(8, product.getIsPublic());
            stmt.setObject(9, product.getLifecycleStatus());
            stmt.setTimestamp(10, new Timestamp(System.currentTimeMillis())); // createdatetime
            stmt.setTimestamp(11, new Timestamp(System.currentTimeMillis())); // lastupdatedatetime
            stmt.setObject(12, product.getCreatedById()); // createdby_id
            stmt.setObject(13, product.getLastUpdateUserId()); // lastupdate_userid
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                return newId;
            }
            
            return -1;
        }
    }

    /**
     * Insert a product using the given connection (for bulk upload so the same transaction can commit/rollback).
     * Does not close the connection.
     */
    public int insertProduct(Product product, Connection conn) throws SQLException {
        int newId = generateNewProductId(conn);
        try (PreparedStatement stmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, newId);
            stmt.setString(2, product.getPrimaryName());
            stmt.setString(3, product.getDescription());
            stmt.setString(4, product.getRefNumber());
            stmt.setString(5, product.getLongName());
            stmt.setObject(6, product.getParentId());
            stmt.setObject(7, product.getStatus());
            stmt.setObject(8, product.getIsPublic());
            stmt.setObject(9, product.getLifecycleStatus());
            stmt.setTimestamp(10, new Timestamp(System.currentTimeMillis()));
            stmt.setTimestamp(11, new Timestamp(System.currentTimeMillis()));
            stmt.setObject(12, product.getCreatedById());
            stmt.setObject(13, product.getLastUpdateUserId());
            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                return newId;
            }
            return -1;
        }
    }
    
    private int generateNewProductId(Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COALESCE(MAX(id), 0) + 1 FROM product");
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1; // Default to 1 if no products exist
        }
    }

    public boolean updateProduct(Product product) throws SQLException {
        // Step 1: Get old values before update
        Product oldProduct = getProductById(product.getId());
        if (oldProduct == null) {
            throw new SQLException("Product not found with ID: " + product.getId());
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPDATE)) {
            
            stmt.setString(1, product.getPrimaryName());
            stmt.setString(2, product.getDescription());
            stmt.setString(3, product.getRefNumber());
            stmt.setString(4, product.getLongName());
            stmt.setObject(5, product.getParentId());
            stmt.setObject(6, product.getStatus());
            stmt.setObject(7, product.getIsPublic());
            stmt.setObject(8, product.getLifecycleStatus());
            stmt.setTimestamp(9, new Timestamp(System.currentTimeMillis())); // lastupdate_datetime
            stmt.setInt(10, product.getLastUpdateUserId());
            stmt.setInt(11, product.getId());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                // Step 2: Create audit records for updates
                try {
                    String userName = getPersonFullName(product.getLastUpdateUserId());
                    if (userName != null) {
                        createProductUpdateAuditRecords(product.getId(), oldProduct, product, userName);
                        //system.out.println("✅ Product update audit records created for ID: " + product.getId());
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error creating product update audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the update if audit fails
                }

                // Step 3: Create snapshot in product_audit
                try {
                    createProductUpdateAuditSnapshot(product.getId());
                    //system.out.println("✅ ProductDAO: product_audit update snapshot created for ID: " + product.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating product_audit update snapshot: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            return affectedRows > 0;
        }
    }

    public boolean deleteProduct(int id, int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE)) {
            
            stmt.setInt(1, userId);
            stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            stmt.setInt(3, id);
            
            return stmt.executeUpdate() > 0;
        }
    }

    private Product mapResultSetToProduct(ResultSet rs) throws SQLException {
        Product product = new Product();
        
        product.setId(rs.getInt("id"));
        product.setParentId(rs.getObject("parent_id", Integer.class));
        product.setIsPublic(rs.getObject("is_public", Integer.class));
        product.setStatus(rs.getObject("status", Integer.class));
        try {
            product.setStatusName(rs.getString("statusName"));
        } catch (SQLException e) {
            product.setStatusName(null);
        }
        product.setLifecycleStatus(rs.getObject("lifecycle_status", Integer.class));
        product.setPrimaryName(rs.getString("primaryname"));
        product.setDescription(rs.getString("description"));
        product.setRefNumber(rs.getString("refnumber"));
        product.setLongName(rs.getString("longname"));
        product.setCreatedDatetime(rs.getTimestamp("createdatetime"));
        product.setCreatedById(rs.getObject("createdby_id", Integer.class));
        product.setLastUpdateUserId(rs.getObject("lastupdate_userid", Integer.class));
        product.setLastUpdatedDatetime(rs.getTimestamp("lastupdatedatetime"));
        
        return product;
    }

    // RefNumber uniqueness methods
    public Product getProductByRefNumber(String refNumber) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_REFNUMBER)) {
            
            pstmt.setString(1, refNumber);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToProduct(rs);
                }
            }
        }
        
        return null;
    }

    public Product getProductByRefNumberExcludingId(String refNumber, int excludeId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_REFNUMBER_EXCLUDE_ID)) {
            
            pstmt.setString(1, refNumber);
            pstmt.setInt(2, excludeId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToProduct(rs);
                }
            }
        }
        
        return null;
    }

    /**
     * Check if RefNumber is unique (for create operations).
     * Uses centralized RefNumberValidator for consistent validation across all facets.
     */
    public boolean isRefNumberUnique(String refNumber) throws SQLException {
        return com.example.budg_v2.util.RefNumberValidator.isRefNumberUnique("Product", refNumber);
    }

    /**
     * Check if RefNumber is unique for update (excluding current ID).
     * Uses centralized RefNumberValidator for consistent validation across all facets.
     * Rule: Can repeat ref if it's the same object (excludeId), but cannot repeat for different objects.
     */
    public boolean isRefNumberUniqueForUpdate(String refNumber, int excludeId) throws SQLException {
        return com.example.budg_v2.util.RefNumberValidator.isRefNumberUniqueForUpdate("Product", refNumber, excludeId);
    }

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int productId, String updateType, String field, String value, String userName) throws SQLException {
        auditStmt.setInt(1, productId);        // id
        auditStmt.setString(2, updateType);    // updateType
        auditStmt.setString(3, field);          // field
        auditStmt.setString(4, value);         // to
        auditStmt.setString(5, userName);       // author
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * إنشاء audit records للمنتج الجديد
     * يتم استدعاء هذا method بعد إنشاء المنتج بنجاح
     */
    public void createProductAuditRecords(int productId, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            
            // 1. الحصول على بيانات المنتج
            String productDataSql = "SELECT * FROM product WHERE id = ?";
            PreparedStatement productStmt = conn.prepareStatement(productDataSql);
            productStmt.setInt(1, productId);
            ResultSet productRs = productStmt.executeQuery();
            
            if (!productRs.next()) {
                throw new SQLException("Product not found with ID: " + productId);
            }

            // 2. إعداد audit statement
            String auditSql = """
                INSERT INTO product_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Product', 'Details', ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            String primaryName = productRs.getString("primaryname");
            if (primaryName != null && !primaryName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, productId, "Added", "Primary Name", primaryName, userName);
            }
            
            // Description
            String description = productRs.getString("description");
            if (description != null && !description.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, productId, "Added", "Description", description, userName);
            }
            
            // Reference Number
            String refNumber = productRs.getString("refnumber");
            if (refNumber != null && !refNumber.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, productId, "Added", "Reference Number", refNumber, userName);
            }
            
            // Long Name
            String longName = productRs.getString("longname");
            if (longName != null && !longName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, productId, "Added", "Long Name", longName, userName);
            }
            
            // Parent Product (store Name for readability)
            Integer parentId = productRs.getObject("parent_id", Integer.class);
            if (parentId != null) {
                String parentName = getProductName(parentId);
                if (parentName != null) {
                    createNewAuditRecord(conn, auditStmt, productId, "Added", "Parent Product", parentName, userName);
                }
            }
            
            // Status (store Name for readability)
            Integer statusId = productRs.getObject("status", Integer.class);
            if (statusId != null) {
                String statusName = getStatusPrimaryName(statusId);
                if (statusName != null) {
                    createNewAuditRecord(conn, auditStmt, productId, "Status Change", "Status", statusName, userName);
                }
            }
            
            // Lifecycle Status (store Name for readability)
            Integer lifecycleStatusId = productRs.getObject("lifecycle_status", Integer.class);
            if (lifecycleStatusId != null) {
                String lifecycleStatusName = getProductLifecycleStatusName(lifecycleStatusId);
                if (lifecycleStatusName != null) {
                    createNewAuditRecord(conn, auditStmt, productId, "Status Change", "Lifecycle Status", lifecycleStatusName, userName);
                }
            }
            
            // Is Public (store Name for readability)
            Integer isPublic = productRs.getObject("is_public", Integer.class);
            if (isPublic != null) {
                String isPublicName = getViewingName(isPublic);
                if (isPublicName != null) {
                    createNewAuditRecord(conn, auditStmt, productId, "Added", "Is Public", isPublicName, userName);
                }
            }
            
            
            // Last Update User
            Integer lastUpdateUserId = productRs.getObject("lastupdate_userid", Integer.class);
            if (lastUpdateUserId != null) {
                String lastUpdateUserName = getPersonFullName(lastUpdateUserId);
                if (lastUpdateUserName != null) {
                    createNewAuditRecord(conn, auditStmt, productId, "Added", "Last Update User", lastUpdateUserName, userName);
                }
            }
            
            conn.commit(); // تأكيد الـ transaction
            
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback(); // إلغاء الـ transaction في حالة الخطأ
            }
            throw e;
        } finally {
            // تنظيف الموارد
            if (auditStmt != null) auditStmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * إنشاء سجل في جدول product_audit بعد إنشاء المنتج
     * يتم استدعاء هذا method بعد إنشاء المنتج بنجاح
     */
    public void createProductAuditRecord(int productId) throws SQLException {
        String sql = """
            INSERT INTO product_audit (
                id, parent_id, lifecycle_status, status, is_public, refnumber, primaryname, longname, 
                description, createdatetime, lastupdatedatetime, deleteddatetime, createdby_id, lastupdate_userid, revtype
            )
            SELECT 
                id, parent_id, lifecycle_status, status, is_public, refnumber, primaryname, longname, 
                description, createdatetime, lastupdatedatetime, NULL, createdby_id, lastupdate_userid, 'Added'
            FROM product 
            WHERE id = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.executeUpdate();
        }
    }

    // Helper methods للحصول على الأسماء
    private String getProductName(int productId) throws SQLException {
        String sql = "SELECT primaryname FROM product WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    private String getStatusPrimaryName(int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM status WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    private String getProductLifecycleStatusName(int lifecycleStatusId) throws SQLException {
        String sql = "SELECT primaryname FROM product_lifecycle WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleStatusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    private String getPersonFullName(int personId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("fullName");
            }
        }
        return null;
    }

    private String getViewingName(int viewingId) throws SQLException {
        String sql = "SELECT Name FROM viewing WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, viewingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    /**
     * إنشاء audit records للـ stakeholder بعد إنشاء المنتج
     * يتم استدعاء هذا method بعد إنشاء المنتج بنجاح
     */
    public void createStakeholderAuditRecords(int productId, String userName, String userFullName, int roleId) throws SQLException {
        Connection conn = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            
            // 1. إعداد audit statement للـ stakeholder
            String auditSql = """
                INSERT INTO product_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Stakeholder', 'link', ?, ?, NULL, ?, ?)
            """;
            
            // الحصول على roleID الفعلي من object_x_people
            Integer actualRoleId = getStakeholderRoleId(productId);
            if (actualRoleId == null) {
                actualRoleId = roleId; // fallback to the passed roleId
            }
            
            // الحصول على اسم الدور من object_role بناءً على roleID من object_x_people
            String roleName = getRoleName(actualRoleId);
            if (roleName == null) roleName = "Product Owner"; // fallback
            
            // Role
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
                createNewAuditRecord(conn, auditStmt, productId, "Added", "Role", roleName, userName);
            }
            
            // Role Status - الحصول على statusID من object_x_people ثم اسم الـ status
            Integer statusId = getStakeholderStatusId(productId);
            String statusName = "Active"; // fallback
            if (statusId != null) {
                String fetchedStatusName = getStatusNameById(statusId);
                if (fetchedStatusName != null) statusName = fetchedStatusName;
            }
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
                createNewAuditRecord(conn, auditStmt, productId, "Added", "Role Status", statusName, userName);
            }
            
            // Name
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
                createNewAuditRecord(conn, auditStmt, productId, "Added", "Name", userFullName, userName);
            }
            
            conn.commit(); // تأكيد الـ transaction
            
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback(); // إلغاء الـ transaction في حالة الخطأ
            }
            throw e;
        } finally {
            // تنظيف الموارد
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    // Helper methods للحصول على أسماء الـ stakeholders
    private String getRoleName(int roleId) throws SQLException {
        String sql = "SELECT primaryname FROM object_role WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }
    
    // Get actual roleID from object_x_people for the stakeholder
    private Integer getStakeholderRoleId(int productId) throws SQLException {
        String sql = "SELECT oxp.roleID FROM object_x_people oxp " +
                    "JOIN product_x_objectxpeople pxo ON pxo.object_x_ip = oxp.id " +
                    "WHERE pxo.product_id = ? " +
                    "ORDER BY pxo.id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("roleID");
            }
        }
        return null;
    }
    
    // Get statusID from object_x_people for the stakeholder
    private Integer getStakeholderStatusId(int productId) throws SQLException {
        String sql = "SELECT oxp.statusID FROM object_x_people oxp " +
                    "JOIN product_x_objectxpeople pxo ON pxo.object_x_ip = oxp.id " +
                    "WHERE pxo.product_id = ? " +
                    "ORDER BY pxo.id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("statusID");
            }
        }
        return null;
    }
    
    // Get status name from object_x_ip_status by statusID
    private String getStatusNameById(int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM object_x_ip_status WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    // ============================================
    // UPDATE AUDIT METHODS
    // ============================================

    /**
     * Create audit records when updating a product
     * Compares old and new values and logs the differences
     */
    public void createProductUpdateAuditRecords(int productId, Product oldProduct, Product newProduct, String userName) throws SQLException {
        //system.out.println("🔍 ProductDAO.createProductUpdateAuditRecords - START for ID: " + productId);
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            String auditSql = """
                INSERT INTO product_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            if (!isEqual(oldProduct.getPrimaryName(), newProduct.getPrimaryName())) {
                createUpdateAuditRecord(conn, auditStmt, productId, "Product", "Details", 
                    "Updated", "Primary Name", oldProduct.getPrimaryName(), newProduct.getPrimaryName(), userName);
            }
            
            // Description
            if (!isEqual(oldProduct.getDescription(), newProduct.getDescription())) {
                createUpdateAuditRecord(conn, auditStmt, productId, "Product", "Details", 
                    "Updated", "Description", oldProduct.getDescription(), newProduct.getDescription(), userName);
            }
            
            // Reference Number
            if (!isEqual(oldProduct.getRefNumber(), newProduct.getRefNumber())) {
                createUpdateAuditRecord(conn, auditStmt, productId, "Product", "Details", 
                    "Updated", "Reference Number", oldProduct.getRefNumber(), newProduct.getRefNumber(), userName);
            }
            
            // Long Name
            if (!isEqual(oldProduct.getLongName(), newProduct.getLongName())) {
                createUpdateAuditRecord(conn, auditStmt, productId, "Product", "Details", 
                    "Updated", "Long Name", oldProduct.getLongName(), newProduct.getLongName(), userName);
            }
            
            // Parent Product (store Name for readability in audit_history)
            if (!isEqual(oldProduct.getParentId(), newProduct.getParentId())) {
                String oldParentName = oldProduct.getParentId() != null ? getProductName(conn, oldProduct.getParentId()) : null;
                String newParentName = newProduct.getParentId() != null ? getProductName(conn, newProduct.getParentId()) : null;
                createUpdateAuditRecord(conn, auditStmt, productId, "Product", "Details", 
                    "Updated", "Parent Product", oldParentName, newParentName, userName);
            }
            
            // Status (store Name for readability in audit_history)
            if (!isEqual(oldProduct.getStatus(), newProduct.getStatus())) {
                String oldStatusName = oldProduct.getStatus() != null ? getStatusPrimaryName(conn, oldProduct.getStatus()) : null;
                String newStatusName = newProduct.getStatus() != null ? getStatusPrimaryName(conn, newProduct.getStatus()) : null;
                createUpdateAuditRecord(conn, auditStmt, productId, "Product", "Details", 
                    "Status Change", "Status", oldStatusName, newStatusName, userName);
            }
            
            // Lifecycle Status (store Name for readability in audit_history)
            if (!isEqual(oldProduct.getLifecycleStatus(), newProduct.getLifecycleStatus())) {
                String oldLifecycleName = oldProduct.getLifecycleStatus() != null ? getProductLifecycleStatusName(conn, oldProduct.getLifecycleStatus()) : null;
                String newLifecycleName = newProduct.getLifecycleStatus() != null ? getProductLifecycleStatusName(conn, newProduct.getLifecycleStatus()) : null;
                createUpdateAuditRecord(conn, auditStmt, productId, "Product", "Details", 
                    "Status Change", "Lifecycle Status", oldLifecycleName, newLifecycleName, userName);
            }
            
            // Is Public (store Name for readability in audit_history)
            if (!isEqual(oldProduct.getIsPublic(), newProduct.getIsPublic())) {
                String oldIsPublicName = oldProduct.getIsPublic() != null ? getViewingName(conn, oldProduct.getIsPublic()) : null;
                String newIsPublicName = newProduct.getIsPublic() != null ? getViewingName(conn, newProduct.getIsPublic()) : null;
                createUpdateAuditRecord(conn, auditStmt, productId, "Product", "Details", 
                    "Updated", "Is Public", oldIsPublicName, newIsPublicName, userName);
            }
            
            conn.commit();
            //system.out.println("✅ ProductDAO.createProductUpdateAuditRecords - COMMITTED successfully");
            
        } catch (SQLException e) {
            System.err.println("❌ ProductDAO.createProductUpdateAuditRecords - ERROR: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (auditStmt != null) auditStmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Helper method to create individual audit record for update with from and to values
     */
    private int createUpdateAuditRecord(Connection conn, PreparedStatement auditStmt, 
            int productId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        
        auditStmt.setInt(1, productId);
        auditStmt.setString(2, object);
        auditStmt.setString(3, event);
        auditStmt.setString(4, updateType);
        auditStmt.setString(5, field);
        auditStmt.setString(6, fromValue);      // from
        auditStmt.setString(7, toValue);        // to
        auditStmt.setString(8, userName);
        
        auditStmt.executeUpdate();
        //system.out.println("    ✓ Update audit record inserted");
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * Method to create a new snapshot in product_audit when updating
     */
    public void createProductUpdateAuditSnapshot(int productId) throws SQLException {
        //system.out.println("🔍 ProductDAO.createProductUpdateAuditSnapshot - Creating update snapshot for ID: " + productId);
        String sql = """
            INSERT INTO product_audit (
                id, parent_id, lifecycle_status, status, is_public, refnumber, 
                primaryname, longname, description, createdatetime, lastupdatedatetime, 
                deleteddatetime, createdby_id, lastupdate_userid, revtype
            )
            SELECT 
                id, parent_id, lifecycle_status, status, is_public, refnumber, 
                primaryname, longname, description, createdatetime, lastupdatedatetime, 
                deleteddatetime, createdby_id, lastupdate_userid, 'Updated'
            FROM product 
            WHERE id = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error creating update snapshot: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Helper method to normalize values (convert null, empty string, and 0 to null)
     */
    private String normalizeValue(Object value) {
        if (value == null) return null;
        
        // Handle Integer with value 0 (considered null in foreign key context)
        if (value instanceof Integer && ((Integer) value) == 0) {
            return null;
        }
        
        String strValue = value.toString().trim();
        // Convert empty string, "null" string, and "0" string to null
        if (strValue.isEmpty() || strValue.equalsIgnoreCase("null") || strValue.equals("0")) {
            return null;
        }
        return strValue;
    }

    /**
     * Helper method for safe comparison between values (handles null, empty strings, and 0)
     */
    private boolean isEqual(Object obj1, Object obj2) {
        // Normalize values first
        String normalized1 = normalizeValue(obj1);
        String normalized2 = normalizeValue(obj2);
        
        // Compare after normalization
        if (normalized1 == null && normalized2 == null) return true;
        if (normalized1 == null || normalized2 == null) return false;
        return normalized1.equals(normalized2);
    }

    // ============================================
    // OVERLOADED HELPER METHODS WITH CONNECTION PARAMETER
    // (To avoid connection leaks in transactions)
    // ============================================

    /**
     * Get product name using existing connection
     */
    private String getProductName(Connection conn, int productId) throws SQLException {
        String sql = "SELECT primaryname FROM product WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    /**
     * Get status name using existing connection
     */
    private String getStatusPrimaryName(Connection conn, int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM status WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    /**
     * Get product lifecycle status name using existing connection
     */
    private String getProductLifecycleStatusName(Connection conn, int lifecycleStatusId) throws SQLException {
        String sql = "SELECT primaryname FROM product_lifecycle WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleStatusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    /**
     * Get viewing name using existing connection
     */
    private String getViewingName(Connection conn, int viewingId) throws SQLException {
        String sql = "SELECT Name FROM viewing WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, viewingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    /**
     * حذف المنتج مع تسجيل audit records
     */
    public boolean deleteProductWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // الخطوة 1: الحذف الناعم
            String deleteSql = "UPDATE product SET DeletedDatetime = NOW() WHERE ID = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit history record
                String auditSql = """
                    INSERT INTO product_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
                """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "Product");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Product");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                // الخطوة 3: إنشاء snapshot في product_audit
                String snapshotSql = """
                    INSERT INTO product_audit (
                        ID, Parent_ID, Business_Area_ID, Is_Public, Classification, Status, Lifecycle,
                        RefNumber, PrimaryName, LongName, Description, CreateDatetime, LastUpdateDatetime,
                        DeleteDatetime, LastUpdate_UserID, RevType
                    )
                    SELECT 
                        ID, Parent_ID, Business_Area_ID, Is_Public, Classification, Status, Lifecycle,
                        RefNumber, PrimaryName, LongName, Description, CreateDatetime, LastUpdateDatetime,
                        DeleteDatetime, LastUpdate_UserID, 'Deleted'
                    FROM product 
                    WHERE ID = ?
                """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                }
                
                //system.out.println("✅ Product deleted with audit for ID: " + id);
            }
            
            conn.commit();
            return affectedRows > 0;
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("❌ Error during rollback: " + rollbackEx.getMessage());
                }
            }
            throw e;
        } finally {
            try {
                if (deleteStmt != null) deleteStmt.close();
                if (auditStmt != null) auditStmt.close();
            } catch (SQLException e) {
                System.err.println("❌ Error closing statement: " + e.getMessage());
            }
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("❌ Error closing connection: " + e.getMessage());
            }
        }
    }

    /**
     * Create object_x_people record for stakeholder
     * Always creates a NEW record (no reuse)
     */
    public int createObjectXPeople(Connection conn, java.util.Map<String, Object> stakeholder, int currentUserId) throws SQLException {
        String sql = """
            INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdatedatetime, lastupdateuser_id)
            VALUES (NULL, ?, ?, 2, 1, NOW(), ?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, (Integer) stakeholder.get("userId")); // ipid
            ps.setInt(2, (Integer) stakeholder.get("roleId")); // RoleID
            ps.setInt(3, currentUserId); // lastupdateuser_id

            ps.executeUpdate();

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newId = generatedKeys.getInt(1);
                    //system.out.println("✅ Generated object_x_people ID: " + newId);
                    return newId;
                } else {
                    throw new SQLException("Creating object_x_people failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Link stakeholder to product via junction table
     * Prevents duplicate links through DB constraint
     */
    public void linkStakeholderToProduct(Connection conn, int productId, int objectXPeopleId, int currentUserId) throws SQLException {
        String sql = """
            INSERT INTO product_x_objectxpeople (product_id, object_x_ip, lastupdate_userid)
            VALUES (?, ?, ?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, objectXPeopleId);
            ps.setInt(3, currentUserId);
            try {
                ps.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException dup) {
                //system.out.println("⚠️ Link already exists: ProductID=" + productId + ", Object_x_ipid=" + objectXPeopleId);
                // Link already exists, ignore
            }
        }
    }
}
