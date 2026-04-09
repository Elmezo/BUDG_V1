package com.example.budg_v2.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.core.util.Json;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet("/v3/api-docs")
public class OpenApiServlet extends HttpServlet {

    private OpenAPI openAPI;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        ServletContext context = config.getServletContext();

        openAPI = new OpenAPI()
                .info(new Info().title("BUDG V2 API").version("1.0.0")
                        .description("Automatically generated OpenAPI specs for registered Servlets."));

        Map<String, ? extends ServletRegistration> registrations = context.getServletRegistrations();
        for (ServletRegistration reg : registrations.values()) {
            Collection<String> mappings = reg.getMappings();
            String className = reg.getClassName();

            if (className == null)
                continue;

            // Skip internal or third-party servlets
            if (className.startsWith("org.apache.catalina.") || className.startsWith("org.apache.jasper."))
                continue;
            if (className.equals(OpenApiServlet.class.getName()))
                continue;
            if (className.equals(SwaggerUiServlet.class.getName()))
                continue;

            Class<?> clazz;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                continue;
            }

            boolean hasGet = isMethodOverridden(clazz, "doGet");
            boolean hasPost = isMethodOverridden(clazz, "doPost");
            boolean hasPut = isMethodOverridden(clazz, "doPut");
            boolean hasDelete = isMethodOverridden(clazz, "doDelete");

            if (!hasGet && !hasPost && !hasPut && !hasDelete) {
                // Default to GET and POST if we can't determine
                hasGet = true;
                hasPost = true;
            }

            for (String mapping : mappings) {
                String path = mapping;
                if (path.endsWith("/*")) {
                    path = path.substring(0, path.length() - 2) + "/{pathVar}";
                }
                // Normalize any remaining * in the path (e.g. /api/bulk/*/fields, /api/bulk/*/delete-report/*) for OpenAPI 3 compliance
                int wildcardIndex = 0;
                while (path.contains("*")) {
                    String paramName = (wildcardIndex == 0) ? "entity" : "pathVar";
                    path = path.replaceFirst(Pattern.quote("*"), "{" + paramName + "}");
                    wildcardIndex++;
                }

                if (!path.startsWith("/")) {
                    path = "/" + path;
                }

                PathItem pathItem = openAPI.getPaths() != null && openAPI.getPaths().containsKey(path)
                        ? openAPI.getPaths().get(path)
                        : new PathItem();

                String summary = clazz.getSimpleName();
                List<Parameter> pathParams = buildPathParameters(path);

                if (hasGet && pathItem.getGet() == null) {
                    pathItem.setGet(buildOperation(summary, path, "GET", pathParams));
                }
                if (hasPost && pathItem.getPost() == null) {
                    pathItem.setPost(buildOperation(summary, path, "POST", pathParams));
                }
                if (hasPut && pathItem.getPut() == null) {
                    pathItem.setPut(buildOperation(summary, path, "PUT", pathParams));
                }
                if (hasDelete && pathItem.getDelete() == null) {
                    pathItem.setDelete(buildOperation(summary, path, "DELETE", pathParams));
                }

                if (openAPI.getPaths() == null) {
                    io.swagger.v3.oas.models.Paths paths = new io.swagger.v3.oas.models.Paths();
                    paths.addPathItem(path, pathItem);
                    openAPI.paths(paths);
                } else {
                    openAPI.getPaths().addPathItem(path, pathItem);
                }
            }
        }
    }

    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([^}]+)}");

    /** Extracts path parameter names from a path like /api/cr/{pathVar} -> ["pathVar"] */
    private List<Parameter> buildPathParameters(String path) {
        List<Parameter> list = new ArrayList<>();
        Matcher m = PATH_PARAM_PATTERN.matcher(path);
        while (m.find()) {
            String name = m.group(1);
            list.add(new Parameter()
                    .name(name)
                    .in("path")
                    .required(true)
                    .description("Path segment for " + name)
                    .schema(new StringSchema()));
        }
        return list;
    }

    private Operation buildOperation(String summary, String path, String method, List<Parameter> pathParams) {
        Operation op = new Operation()
                .summary(summary)
                .description("Handles " + method + " requests for " + path);
        for (Parameter p : pathParams) {
            op.addParametersItem(p);
        }
        return op;
    }

    private boolean isMethodOverridden(Class<?> clazz, String methodName) {
        Class<?> current = clazz;
        while (current != null && !current.getName().equals("jakarta.servlet.http.HttpServlet")
                && !current.getName().equals("javax.servlet.http.HttpServlet")
                && !current.getName().equals("java.lang.Object")) {
            try {
                current.getDeclaredMethod(methodName, HttpServletRequest.class, HttpServletResponse.class);
                return true;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return false;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");

        String json = Json.mapper().writeValueAsString(openAPI);
        resp.getWriter().write(json);
    }
}
