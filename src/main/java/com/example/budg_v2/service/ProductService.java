package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProductDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Product;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ProductService {
    
    private final ProductDAO productDAO;
    
    public ProductService() {
        this.productDAO = new ProductDAO();
    }
    
    public List<Product> getAllProducts() throws SQLException {
        return productDAO.getAllProducts();
    }

    public List<Product> getAllProducts(int userId) throws SQLException {
        return productDAO.getAllProducts(userId);
    }
    
    public List<Product> getProductsForDropdown() throws SQLException {
        return productDAO.getProductsForDropdown();
    }

    public List<Product> getProductsForDropdown(int userId) throws SQLException {
        return productDAO.getProductsForDropdown(userId);
    }
    
    public Product getProductById(int id) throws SQLException {
        return productDAO.getProductById(id);
    }
    
    public List<Product> searchProducts(String searchTerm) throws SQLException {
        return productDAO.searchProducts(searchTerm);
    }
    
    public int createProduct(Product product, HttpServletRequest request) throws SQLException, IllegalArgumentException {
        if (product.getLastUpdateUserId() == null) {
            product.setLastUpdateUserId(1); // Default user ID
        }
        
        // Auto-generate refnumber if empty (before creation, like project & process)
        if (product.getRefNumber() == null || product.getRefNumber().trim().isEmpty()) {
            try {
                product.setRefNumber(com.example.budg_v2.util.ReferenceNumberGenerator.generateProductReference());
            } catch (SQLException e) {
                System.err.println("Failed to auto-generate product ref number: " + e.getMessage());
                // Continue with null ref number
            }
        } else {
            // Validate refnumber uniqueness if provided
            if (!productDAO.isRefNumberUnique(product.getRefNumber())) {
                throw new IllegalArgumentException("This reference number is already in use. Please enter a unique reference number.");
            }
        }
        
        int productId = productDAO.insertProduct(product);
        
        // Create audit records for history tracking
        try {
            // Get current user name from request attributes
            String userName = getCurrentUserName(request);
            productDAO.createProductAuditRecords(productId, userName);
            productDAO.createProductAuditRecord(productId);
            
            // Create default stakeholder audit records if lastUpdateUserId is provided
            if (product.getLastUpdateUserId() != null) {
                try {
                    String userFullName = getPersonFullName(product.getLastUpdateUserId());
                    if (userFullName != null) {
                        // Create stakeholder audit records with default role (1 = Product Owner)
                        productDAO.createStakeholderAuditRecords(productId, userName, userFullName, 1);
                        //system.out.println("✅ Default stakeholder audit records created for product ID: " + productId);
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error creating default stakeholder audit records: " + e.getMessage());
                    // Continue - don't fail the main operation
                }
            }
        } catch (SQLException e) {
            // Log the error but don't fail the main operation
            System.err.println("Failed to create history audit records: " + e.getMessage());
        }
        
        return productId;
    }
    
    public boolean updateProduct(Product product) throws SQLException, IllegalArgumentException {
        if (product.getLastUpdateUserId() == null) {
            product.setLastUpdateUserId(1); // Default user ID
        }
        
        // Validate refnumber uniqueness for update
        if (product.getRefNumber() != null && !product.getRefNumber().trim().isEmpty()) {
            if (!productDAO.isRefNumberUniqueForUpdate(product.getRefNumber(), product.getId())) {
                throw new IllegalArgumentException("This reference number is already in use. Please enter a unique reference number.");
            }
        }
        
        return productDAO.updateProduct(product);
    }
    
    public boolean deleteProduct(int id, HttpServletRequest request) throws SQLException {
        String userName = getCurrentUserName(request);
        return productDAO.deleteProductWithAudit(id, userName);
    }

    // Helper method to get current user name from request
    private String getCurrentUserName(HttpServletRequest request) {
        Object userNameObj = request.getAttribute("userName");
        if (userNameObj != null) {
            return userNameObj.toString();
        }
        return "System"; // Fallback if no user name found
    }

    // Helper method to get person full name
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
}
