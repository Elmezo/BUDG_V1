package com.example.budg_v2;

import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API endpoint for getting segments accessible by the current user
 * GET /api/segments/accessible - Returns list of segments user has access to
 */
@WebServlet("/api/segments/accessible")
public class SegmentAccessServlet extends HttpServlet {
    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        
        try {
            Integer userIdObj = UserContextUtil.getCurrentUserIdOrNull(request);
            List<Map<String, Object>> segments;
            if (userIdObj == null || userIdObj <= 0) {
                // Guest/anonymous: return only Enterprise segment so UI works without login
                segments = SegmentAccessService.getSegmentsForAnonymousUser();
            } else {
                int userId = userIdObj;
                segments = SegmentAccessService.getUserAccessibleSegments(userId);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("segments", segments);
            result.put("count", segments.size());
            
            //system.out.println("✅ Returning " + segments.size() + " accessible segments for user " + userId);
            if (segments.size() > 0) {
                //system.out.println("📋 Segments: " + segments.stream()
                //    .map(s -> s.get("id") + ":" + s.get("name"))
                   // .collect(java.util.stream.Collectors.joining(", ")));
            }
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            System.err.println("❌ Error in /api/segments/accessible: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            
            response.getWriter().write(gson.toJson(error));
        }
    }
}

