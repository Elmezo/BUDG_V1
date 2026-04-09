package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProductLifecycleDAO;
import com.example.budg_v2.model.ProductLifecycle;

import java.sql.SQLException;
import java.util.List;

public class ProductLifecycleService {
    
    private final ProductLifecycleDAO productLifecycleDAO;
    
    public ProductLifecycleService() {
        this.productLifecycleDAO = new ProductLifecycleDAO();
    }
    
    public List<ProductLifecycle> getAllProductLifecycles() throws SQLException {
        return productLifecycleDAO.getAllProductLifecycles();
    }
}
