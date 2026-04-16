package com.example.budg_v2;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Product;
import com.example.budg_v2.service.ProductService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.RequestedSegmentFilterUtil;
import com.example.budg_v2.util.SegmentScopedPrimaryNameCheck;
import com.example.budg_v2.util.SegmentResponseUtil;
import com.example.budg_v2.util.SegmentResponseUtil.SegmentInfo;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@WebServlet("/api/product/*")
public class ProductServlet extends HttpServlet {

    private ProductService productService;
    private SegmentDAO segmentDAO;
    private SegmentValidationService segmentValidationService;

    @Override
    public void init() {
        this.productService = new ProductService();
        this.segmentDAO = new SegmentDAO();
        this.segmentValidationService = new SegmentValidationService();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        
        try {
            int userId = UserContextUtil.getCurrentUserId(request);
            
            if (pathInfo == null || "/list".equals(pathInfo)) {
                // Return all products
                //system.out.println("ProductServlet: Getting all products (userId: " + userId + ")");
                List<Product> products = userId > 0 ? 
                    productService.getAllProducts(userId) : 
                    productService.getAllProducts();
                products = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        products,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "Product",
                        Product::getId);
                //system.out.println("ProductServlet: Retrieved " + products.size() + " products");
                
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Product p : products) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", p.getId());
                    o.addProperty("primaryname", p.getPrimaryName());
                    o.addProperty("description", p.getDescription());
                    o.addProperty("refnumber", p.getRefNumber());
                    o.addProperty("parentid", p.getParentId());
                    o.addProperty("longname", p.getLongName());
                    o.addProperty("status", p.getStatus());
                    o.addProperty("lifecycle_status", p.getLifecycleStatus());
                    o.addProperty("is_public", p.getIsPublic());
                    arr.add(o);
                    //system.out.println("ProductServlet: Added product to response: " + p.getPrimaryName());
                }
                response.getWriter().write(arr.toString());
            } else if ("/hierarchy".equals(pathInfo)) {
                // Get all products for hierarchy display
                //system.out.println("ProductServlet /hierarchy - Starting hierarchy request (userId: " + userId + ")");
                List<Product> products = userId > 0 ? 
                    productService.getAllProducts(userId) : 
                    productService.getAllProducts();
                //system.out.println("ProductServlet /hierarchy - products count: " + products.size());
                
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Product p : products) {
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("id", p.getId());
                o.addProperty("primaryname", p.getPrimaryName());
                o.addProperty("description", p.getDescription());
                o.addProperty("refnumber", p.getRefNumber());
                o.addProperty("parentid", p.getParentId());
                arr.add(o);
                    //system.out.println("ProductServlet: Added product to hierarchy: " + p.getPrimaryName() + " (ID: " + p.getId() + ", Parent: " + p.getParentId() + ")");
                }
                //system.out.println("ProductServlet /hierarchy - JSON response size: " + arr.size());
                response.getWriter().write(arr.toString());
            } else if ("/parent-picker".equals(pathInfo)) {
                List<Product> products = userId > 0 ? 
                    productService.getProductsForDropdown(userId) : 
                    productService.getProductsForDropdown();
                products = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        products,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "Product",
                        Product::getId);
                
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Product p : products) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", p.getId());
                    o.addProperty("primaryname", p.getPrimaryName());
                    o.addProperty("description", p.getDescription());
                    o.addProperty("refnumber", p.getRefNumber());
                    o.addProperty("parentid", p.getParentId());
                    o.addProperty("longname", p.getLongName());
                    o.addProperty("status", p.getStatus());
                    o.addProperty("lifecycle_status", p.getLifecycleStatus());
                    o.addProperty("is_public", p.getIsPublic());
                    arr.add(o);
                }
                response.getWriter().write(arr.toString());

            } else if ("/search".equals(pathInfo)) {
                String searchQuery = request.getParameter("q");
                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    searchProducts(response, searchQuery.trim());
                } else {
                    getAllProducts(response);
                }
            } else if (pathInfo != null && pathInfo.length() > 1) {
                String idParam = pathInfo.substring(1);
                try {
                    int id = Integer.parseInt(idParam);
                    getProductById(request, response, id);
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid product ID: " + idParam, 400);
                } catch (Exception e) {
                    System.err.println("ProductServlet GET error: " + e.getMessage());
                    e.printStackTrace();
                    JsonUtil.sendErrorResponse(response.getWriter(), "Error retrieving product: " + e.getMessage(), 500);
                }
            } else {
                getAllProducts(response);
            }
        } catch (Exception e) {
            System.err.println("ProductServlet error: " + e.getMessage());
            e.printStackTrace();
            JsonUtil.sendErrorResponse(response.getWriter(), "Error: " + e.getMessage(), 500);
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        // Check create permission for new product creation
        if (!PermissionCheckUtil.checkCreatePermission(request, response, "Product")) {
            return; // Response already sent
        }

        try {
            JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

            String primaryName = JsonUtil.getJsonString(jsonData, "primaryname");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer parentId = JsonUtil.getJsonInt(jsonData, "parent_id");
            Integer status = JsonUtil.getJsonInt(jsonData, "status");
            Integer lifecycleStatus = JsonUtil.getJsonInt(jsonData, "lifecycle_status");
            String refNumber = JsonUtil.getJsonString(jsonData, "refnumber");
            String longNumber = JsonUtil.getJsonString(jsonData, "longname");
            Integer isPublic = JsonUtil.getJsonInt(jsonData, "is_public");
            
            // Get both createdById and lastUpdateUserId
            Integer createdById = JsonUtil.getJsonInt(jsonData, "createdById");
            Integer lastUpdateUserId = JsonUtil.getJsonInt(jsonData, "lastUpdateUserId");
            
            // Fallback to old field names for backward compatibility
            if (createdById == null) {
                createdById = JsonUtil.getJsonInt(jsonData, "createdby_id");
            }
            if (lastUpdateUserId == null) {
                lastUpdateUserId = JsonUtil.getJsonInt(jsonData, "lastupdateuser_id");
            }
            
            // Get from request context if still null
            if (createdById == null || lastUpdateUserId == null) {
                int userIdFromContext = UserContextUtil.getCurrentUserId(request);
                if (userIdFromContext > 0) {
                    if (createdById == null) {
                        createdById = userIdFromContext;
                    }
                    if (lastUpdateUserId == null) {
                        lastUpdateUserId = userIdFromContext;
                    }
                } else {
                    JsonUtil.sendErrorResponse(response.getWriter(), "User authentication required", 401);
                    return;
                }
            }

            // Handle dates
            Timestamp startDate = null;
            Timestamp endDate = null;
            String startDateStr = JsonUtil.getJsonString(jsonData, "startdate");
            String endDateStr = JsonUtil.getJsonString(jsonData, "enddate");
            
            if (startDateStr != null && !startDateStr.trim().isEmpty()) {
                startDate = Timestamp.valueOf(startDateStr + " 00:00:00");
            }
            if (endDateStr != null && !endDateStr.trim().isEmpty()) {
                endDate = Timestamp.valueOf(endDateStr + " 23:59:59");
            }

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Product name is required", 400);
                return;
            }

            if (description == null || description.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Description is required", 400);
                return;
            }

            Integer segmentIdForName = JsonUtil.getJsonInt(jsonData, "segmentId");
            if (segmentIdForName == null) {
                segmentIdForName = 1;
            }
            try (Connection conn = DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "Product", primaryName.trim(),
                        segmentIdForName.longValue(), null)) {
                    JsonUtil.sendErrorResponse(response.getWriter(),
                            "A product with this name already exists in the selected segment.", 400);
                    return;
                }
            }

            Product product = new Product();
            product.setPrimaryName(primaryName.trim());
            product.setDescription(description.trim());
            product.setParentId(parentId);
            product.setStatus(status);
            product.setLifecycleStatus(lifecycleStatus);
            product.setRefNumber(refNumber);
            product.setLongName(longNumber);
            product.setIsPublic(isPublic);
            product.setStartDate(startDate);
            product.setEndDate(endDate);
            product.setCreatedById(createdById);
            product.setLastUpdateUserId(lastUpdateUserId);

            // Validate segment hierarchy before creating product
            Integer segmentId = JsonUtil.getJsonInt(jsonData, "segmentId");
            if (segmentId == null) segmentId = 1;
            if (parentId != null && parentId > 0) {
                try {
                    var hierarchyResult = segmentValidationService.validateParentChildSegment(parentId, segmentId, "Product");
                    if (!hierarchyResult.isValid) {
                        JsonUtil.sendErrorResponse(response.getWriter(), hierarchyResult.message, 400);
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("Error validating product hierarchy: " + e.getMessage());
                    JsonUtil.sendErrorResponse(response.getWriter(), "Error validating segment hierarchy: " + e.getMessage(), 500);
                    return;
                }
            }

            //system.out.println("ProductServlet: Creating product: " + product.getPrimaryName());
            int productId = productService.createProduct(product, request);
            
            if (productId > 0) {
                //system.out.println("ProductServlet: Product created successfully with ID: " + productId);
                int userId = UserContextUtil.getCurrentUserId(request);
                try {
                    segmentDAO.assignObjectToSegment(segmentId, productId, "Product", userId > 0 ? userId : 1);
                    //system.out.println("✅ Product " + productId + " assigned to segment " + segmentId);
                } catch (Exception e) {
                    System.err.println("❌ Error assigning product to segment: " + e.getMessage());
                }
                
                // Assign creator role to the user for the new product
                if (userId > 0) {
                    //system.out.println("✅ Calling assignCreatorRole...");
                    try {
                        assignCreatorRole(productId, userId);
                    } catch (Exception e) {
                        System.err.println("❌ Error assigning creator role: " + e.getMessage());
                        e.printStackTrace();
                        // Continue - don't fail the entire save
                    }
                } else {
                    System.err.println("⚠️ No valid userId in session, skipping role assignment");
                }
                
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("message", "Product created successfully");
                responseJson.addProperty("id", productId);
                response.getWriter().write(responseJson.toString());
            } else {
                System.err.println("ProductServlet: Failed to create product");
                JsonUtil.sendErrorResponse(response.getWriter(), "Failed to create product", 500);
            }

        } catch (Exception e) {
            System.err.println("ProductServlet POST error: " + e.getMessage());
            e.printStackTrace();
            JsonUtil.sendErrorResponse(response.getWriter(), "Error creating product: " + e.getMessage(), 500);
        }
    }

    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Product ID is required", 400);
            return;
        }

        String idParam = pathInfo.substring(1);
        try {
            int id = Integer.parseInt(idParam);
            
            // Check role-based edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Product", id)) {
                return; // Response already sent
            }
            updateProduct(response, id, request);
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Invalid product ID: " + idParam, 400);
        } catch (Exception e) {
            System.err.println("ProductServlet PUT error: " + e.getMessage());
            e.printStackTrace();
            JsonUtil.sendErrorResponse(response.getWriter(), "Error updating product: " + e.getMessage(), 500);
        }
    }

    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        if (pathInfo != null && pathInfo.length() > 1) {
            String idParam = pathInfo.substring(1);
            try {
                int id = Integer.parseInt(idParam);
                deleteProduct(request, response, id);
            } catch (NumberFormatException e) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid product ID: " + idParam, 400);
            } catch (Exception e) {
                System.err.println("ProductServlet DELETE error: " + e.getMessage());
                e.printStackTrace();
                JsonUtil.sendErrorResponse(response.getWriter(), "Error deleting product: " + e.getMessage(), 500);
            }
        } else {
            JsonUtil.sendErrorResponse(response.getWriter(), "Product ID is required", 400);
        }
    }

    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void getAllProducts(HttpServletResponse response) throws Exception {
        List<Product> products = productService.getAllProducts();
        JsonUtil.sendJsonResponse(response.getWriter(), products);
    }

    private void getProductById(HttpServletRequest request, HttpServletResponse response, int id) throws Exception {
        Product product = productService.getProductById(id);
        if (product != null) {
            if (!UserContextUtil.isCurrentUserAdmin(request) && "Deleted".equalsIgnoreCase(product.getStatusName())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"This object is not available.\"}");
                return;
            }
            // Check access control for all users (including guests)
            int userId = UserContextUtil.getCurrentUserId(request);

            // GUEST ACCESS CHECK: Only allow public objects in Enterprise segment
            if (userId <= 0) {
                // Guest user - check if object is public and in Enterprise segment
                try {
                    boolean canAccess = SegmentAccessService.canGuestAccessObject(id, "Product");
                    if (!canAccess) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().write("{\"error\":\"Access denied. This resource is not publicly accessible.\"}");
                        return;
                    }
                } catch (SQLException e) {
                    // On error, deny access for safety
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Access denied. Unable to verify access permissions.\"}");
                    return;
                }
            }

            // Authenticated user access check
            if (userId > 0) {
                boolean canAccess = SegmentAccessService.canAccessObject(userId, id, "Product");
                if (!canAccess) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Access denied. You don't have permission to view this product.\"}");
                    return;
                }
            }

            JsonObject productJson = JsonParser.parseString(JsonUtil.toJson(product)).getAsJsonObject();
            SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, id, "Product");
            SegmentResponseUtil.applySegmentInfo(productJson, segmentInfo, request);
            response.getWriter().write(productJson.toString());
        } else {
            JsonUtil.sendErrorResponse(response.getWriter(), "Product not found", 404);
        }
    }

    private void searchProducts(HttpServletResponse response, String searchTerm) throws Exception {
        List<Product> products = productService.searchProducts(searchTerm);
        JsonUtil.sendJsonResponse(response.getWriter(), products);
    }

    private void updateProduct(HttpServletResponse response, int id, HttpServletRequest request) throws Exception {
        // Check segment-based edit permission
        int currentUserId = UserContextUtil.getCurrentUserId(request);
        if (currentUserId > 0) {
            boolean canEdit = SegmentAccessService.canEditObject(currentUserId, id, "Product");
            if (!canEdit) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                JsonUtil.sendErrorResponse(response.getWriter(), "Access denied. You don't have permission to edit this product.", 403);
                return;
            }
        }

        JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

        String primaryName = JsonUtil.getJsonString(jsonData, "primaryname");
        String description = JsonUtil.getJsonString(jsonData, "description");
        Integer parentId = JsonUtil.getJsonInt(jsonData, "parent_id");
        Integer status = JsonUtil.getJsonInt(jsonData, "status");
        Integer lifecycleStatus = JsonUtil.getJsonInt(jsonData, "lifecycle_status");
        String refNumber = JsonUtil.getJsonString(jsonData, "refnumber");
        String longNumber = JsonUtil.getJsonString(jsonData, "longname");
        Integer isPublic = JsonUtil.getJsonInt(jsonData, "is_public");
        Integer lastUpdateUserId = JsonUtil.getJsonInt(jsonData, "lastupdateuser_id");
        if (lastUpdateUserId == null) {
            int userIdFromContext = UserContextUtil.getCurrentUserId(request);
            if (userIdFromContext > 0) {
                lastUpdateUserId = userIdFromContext;
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "User authentication required", 401);
                return;
            }
        }

        // Handle dates
        Timestamp startDate = null;
        Timestamp endDate = null;
        String startDateStr = JsonUtil.getJsonString(jsonData, "startdate");
        String endDateStr = JsonUtil.getJsonString(jsonData, "enddate");
        
        if (startDateStr != null && !startDateStr.trim().isEmpty()) {
            startDate = Timestamp.valueOf(startDateStr + " 00:00:00");
        }
        if (endDateStr != null && !endDateStr.trim().isEmpty()) {
            endDate = Timestamp.valueOf(endDateStr + " 23:59:59");
        }

        if (primaryName == null || primaryName.trim().isEmpty()) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Product name is required", 400);
            return;
        }

        if (description == null || description.trim().isEmpty()) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Description is required", 400);
            return;
        }

        Integer reqSeg = JsonUtil.getJsonInt(jsonData, "segmentId");
        if (reqSeg == null) {
            reqSeg = JsonUtil.getJsonInt(jsonData, "segment_id");
        }
        int curSeg = segmentDAO.getObjectSegmentId(id, "Product");
        long effSeg = (reqSeg != null) ? reqSeg.longValue() : (curSeg > 0 ? curSeg : 1L);
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (SegmentScopedPrimaryNameCheck.exists(conn, "Product", primaryName.trim(), effSeg, id)) {
                JsonUtil.sendErrorResponse(response.getWriter(),
                        "A product with this name already exists in this segment.", 400);
                return;
            }
        }

        Product product = new Product();
        product.setId(id);
        product.setPrimaryName(primaryName.trim());
        product.setDescription(description.trim());
        product.setParentId(parentId);
        product.setStatus(status);
        product.setLifecycleStatus(lifecycleStatus);
        product.setRefNumber(refNumber);
        product.setLongName(longNumber);
        product.setIsPublic(isPublic);
        product.setStartDate(startDate);
        product.setEndDate(endDate);
        product.setLastUpdateUserId(lastUpdateUserId);

        boolean success = false;
        try {
            success = productService.updateProduct(product);
        } catch (IllegalArgumentException e) {
            // Handle duplicate reference number error
            if (e.getMessage() != null && e.getMessage().contains("reference number")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", "Duplicate reference number. This reference is already in use by another product.");
                errorResponse.addProperty("message", "Duplicate reference number. This reference is already in use by another product.");
                response.getWriter().write(errorResponse.toString());
                return;
            }
            // Re-throw if it's a different IllegalArgumentException
            throw e;
        }

        // Update segment assignment if provided - even if the base update returned false.
        // This fixes the case where the user only changes the segment field.
        boolean segmentUpdated = false;
            Integer segmentId = JsonUtil.getJsonInt(jsonData, "segmentId");
            if (segmentId == null) {
                segmentId = JsonUtil.getJsonInt(jsonData, "segment_id");
            }
            if (segmentId != null) {
                try {
                    int currentSegmentId = segmentDAO.getObjectSegmentId(id, "Product");
                    if (currentSegmentId != segmentId) {
                        // Validate full segment move rules before changing segment
                        com.example.budg_v2.service.SegmentValidationService validationService = new com.example.budg_v2.service.SegmentValidationService();
                        var validationResult = validationService.validateSegmentMove(id, segmentId, "Product", parentId);
                        if (!validationResult.isValid) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            JsonObject error = new JsonObject();
                            error.addProperty("success", false);
                            error.addProperty("error", validationResult.message);
                            response.getWriter().write(error.toString());
                            return;
                        }
                        
                        if (currentSegmentId > 0) {
                            segmentDAO.removeObjectFromSegment(currentSegmentId, id, "Product", lastUpdateUserId);
                        }
                        segmentDAO.assignObjectToSegment(segmentId, id, "Product", lastUpdateUserId);
                        //system.out.println("✅ Product " + id + " segment changed from " + currentSegmentId + " to " + segmentId);
                    segmentUpdated = true;
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error updating product segment: " + e.getMessage());
                    JsonUtil.sendErrorResponse(response.getWriter(), "Error updating product segment: " + e.getMessage(), 500);
                    return;
                }
            }

        if (success || segmentUpdated) {
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("message", "Product updated successfully");
            response.getWriter().write(responseJson.toString());
        } else {
            JsonUtil.sendErrorResponse(response.getWriter(), "Failed to update product", 500);
        }
    }

    private void deleteProduct(HttpServletRequest request, HttpServletResponse response, int id) throws Exception {
        boolean success = productService.deleteProduct(id, request);
        if (success) {
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("message", "Product deleted successfully");
            response.getWriter().write(responseJson.toString());
        } else {
            JsonUtil.sendErrorResponse(response.getWriter(), "Failed to delete product", 500);
        }
    }

    private void assignCreatorRole(int productId, int userId) throws SQLException {
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int moduleId = com.example.budg_v2.util.DefaultStakeholderUtil.getModuleId(conn, "Product");
                java.util.List<Integer> rolesToAssign = com.example.budg_v2.util.DefaultStakeholderUtil.getDefaultRolesForCreator(conn, moduleId, userId);

                if (rolesToAssign.isEmpty()) {
                    conn.commit();
                    return;
                }

                for (Integer roleId : rolesToAssign) {
                    try {
                        String insertOXP = """
                                INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdateuser_id)
                                VALUES (NULL, ?, ?, 2, 1, ?)
                                """;
                        int objectXPeopleId;
                        try (java.sql.PreparedStatement stmt = conn.prepareStatement(insertOXP, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                            stmt.setInt(1, userId);
                            stmt.setInt(2, roleId);
                            stmt.setInt(3, userId);
                            if (stmt.executeUpdate() == 0) throw new java.sql.SQLException("Failed to insert into object_x_people");
                            try (java.sql.ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                                if (generatedKeys.next()) {
                                    objectXPeopleId = generatedKeys.getInt(1);
                                } else {
                                    throw new java.sql.SQLException("Failed to get generated key for object_x_people");
                                }
                            }
                        }

                        String insertProductX = """
                                INSERT INTO product_x_objectxpeople (object_x_ip, product_id, lastupdate_userid)
                                VALUES (?, ?, ?)
                                """;
                        try (java.sql.PreparedStatement stmt = conn.prepareStatement(insertProductX)) {
                            stmt.setInt(1, objectXPeopleId);
                            stmt.setInt(2, productId);
                            stmt.setInt(3, userId);
                            if (stmt.executeUpdate() == 0) throw new java.sql.SQLException("Failed to insert into product_x_objectxpeople");
                        }

                        com.example.budg_v2.util.RoleNotificationHelper.createNotificationAfterCreatorRoleAssigned(
                                "Product", productId, userId, roleId, objectXPeopleId, conn);

                    } catch (java.sql.SQLException e) {
                        System.err.println("❌ Error assigning default role " + roleId + " to creator: " + e.getMessage());
                    }
                }

                conn.commit();
            } catch (java.sql.SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (java.sql.SQLException e) {
            System.err.println("❌ Error in assignCreatorRole: " + e.getMessage());
            throw e;
        }
    }


    @SuppressWarnings("unused")
    private Integer getNextObjectXPeopleId(java.sql.Connection conn) throws SQLException {
        String query = "SELECT COALESCE(MAX(ID), 0) + 1 FROM object_x_people";
        try (java.sql.PreparedStatement stmt = conn.prepareStatement(query);
             java.sql.ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 1; // fallback
    }
}
