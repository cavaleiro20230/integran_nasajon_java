[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/NasajonIntegrationApplication.java"
package com.example.nasajonintegration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NasajonIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NasajonIntegrationApplication.class, args);
    }
}
[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/controller/IntegrationController.java"
package com.example.nasajonintegration.controller;

import com.example.nasajonintegration.dto.ExportRequest;
import com.example.nasajonintegration.dto.ImportRequest;
import com.example.nasajonintegration.dto.IntegrationResponse;
import com.example.nasajonintegration.service.IntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/integration")
@Tag(name = "Integration API", description = "API for data integration with Nasajon systems")
public class IntegrationController {

    private final IntegrationService integrationService;

    @Autowired
    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @PostMapping("/export")
    @Operation(summary = "Export data to Nasajon", description = "Exports data from the application to Nasajon systems")
    public ResponseEntity<IntegrationResponse> exportData(@RequestBody ExportRequest request) {
        IntegrationResponse response = integrationService.exportData(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/import")
    @Operation(summary = "Import data from Nasajon", description = "Imports data from Nasajon systems to the application")
    public ResponseEntity<IntegrationResponse> importData(@RequestBody ImportRequest request) {
        IntegrationResponse response = integrationService.importData(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/import/file")
    @Operation(summary = "Import data from file", description = "Imports data from a file into the application")
    public ResponseEntity<IntegrationResponse> importFromFile(@RequestParam("file") MultipartFile file, 
                                                             @RequestParam("type") String type) {
        IntegrationResponse response = integrationService.importFromFile(file, type);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{jobId}")
    @Operation(summary = "Get integration job status", description = "Retrieves the status of an integration job")
    public ResponseEntity<IntegrationResponse> getJobStatus(@PathVariable String jobId) {
        IntegrationResponse response = integrationService.getJobStatus(jobId);
        return ResponseEntity.ok(response);
    }
}
[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/service/IntegrationService.java"
package com.example.nasajonintegration.service;

import com.example.nasajonintegration.dto.ExportRequest;
import com.example.nasajonintegration.dto.ImportRequest;
import com.example.nasajonintegration.dto.IntegrationResponse;
import org.springframework.web.multipart.MultipartFile;

public interface IntegrationService {
    IntegrationResponse exportData(ExportRequest request);
    IntegrationResponse importData(ImportRequest request);
    IntegrationResponse importFromFile(MultipartFile file, String type);
    IntegrationResponse getJobStatus(String jobId);
}
[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/service/impl/IntegrationServiceImpl.java"
package com.example.nasajonintegration.service.impl;

import com.example.nasajonintegration.dto.ExportRequest;
import com.example.nasajonintegration.dto.ImportRequest;
import com.example.nasajonintegration.dto.IntegrationResponse;
import com.example.nasajonintegration.exception.IntegrationException;
import com.example.nasajonintegration.model.IntegrationJob;
import com.example.nasajonintegration.model.JobStatus;
import com.example.nasajonintegration.repository.IntegrationJobRepository;
import com.example.nasajonintegration.service.IntegrationService;
import com.example.nasajonintegration.service.NasajonApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class IntegrationServiceImpl implements IntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationServiceImpl.class);

    private final IntegrationJobRepository jobRepository;
    private final NasajonApiClient nasajonApiClient;

    @Autowired
    public IntegrationServiceImpl(IntegrationJobRepository jobRepository, NasajonApiClient nasajonApiClient) {
        this.jobRepository = jobRepository;
        this.nasajonApiClient = nasajonApiClient;
    }

    @Override
    public IntegrationResponse exportData(ExportRequest request) {
        logger.info("Starting export process for request: {}", request);
        
        // Create and save job
        String jobId = UUID.randomUUID().toString();
        IntegrationJob job = new IntegrationJob();
        job.setJobId(jobId);
        job.setType("EXPORT");
        job.setStatus(JobStatus.PENDING);
        job.setCreatedAt(LocalDateTime.now());
        job.setRequestData(request.toString());
        jobRepository.save(job);
        
        // Process asynchronously
        processExportAsync(jobId, request);
        
        return new IntegrationResponse(jobId, JobStatus.PENDING.name(), "Export job created successfully");
    }

    @Override
    public IntegrationResponse importData(ImportRequest request) {
        logger.info("Starting import process for request: {}", request);
        
        // Create and save job
        String jobId = UUID.randomUUID().toString();
        IntegrationJob job = new IntegrationJob();
        job.setJobId(jobId);
        job.setType("IMPORT");
        job.setStatus(JobStatus.PENDING);
        job.setCreatedAt(LocalDateTime.now());
        job.setRequestData(request.toString());
        jobRepository.save(job);
        
        // Process asynchronously
        processImportAsync(jobId, request);
        
        return new IntegrationResponse(jobId, JobStatus.PENDING.name(), "Import job created successfully");
    }

    @Override
    public IntegrationResponse importFromFile(MultipartFile file, String type) {
        logger.info("Starting file import process for file: {}, type: {}", file.getOriginalFilename(), type);
        
        if (file.isEmpty()) {
            throw new IntegrationException("File is empty");
        }
        
        // Create and save job
        String jobId = UUID.randomUUID().toString();
        IntegrationJob job = new IntegrationJob();
        job.setJobId(jobId);
        job.setType("FILE_IMPORT");
        job.setStatus(JobStatus.PENDING);
        job.setCreatedAt(LocalDateTime.now());
        job.setRequestData("File: " + file.getOriginalFilename() + ", Type: " + type);
        jobRepository.save(job);
        
        // Process asynchronously
        processFileImportAsync(jobId, file, type);
        
        return new IntegrationResponse(jobId, JobStatus.PENDING.name(), "File import job created successfully");
    }

    @Override
    public IntegrationResponse getJobStatus(String jobId) {
        logger.info("Getting status for job: {}", jobId);
        
        IntegrationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IntegrationException("Job not found with ID: " + jobId));
        
        return new IntegrationResponse(
                job.getJobId(),
                job.getStatus().name(),
                job.getResultMessage(),
                job.getCompletedAt() != null ? job.getCompletedAt().toString() : null
        );
    }

    @Async
    protected CompletableFuture<Void> processExportAsync(String jobId, ExportRequest request) {
        IntegrationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IntegrationException("Job not found with ID: " + jobId));
        
        try {
            logger.info("Processing export job: {}", jobId);
            job.setStatus(JobStatus.PROCESSING);
            jobRepository.save(job);
            
            // Call Nasajon API to export data
            boolean success = nasajonApiClient.exportData(request);
            
            if (success) {
                job.setStatus(JobStatus.COMPLETED);
                job.setResultMessage("Export completed successfully");
            } else {
                job.setStatus(JobStatus.FAILED);
                job.setResultMessage("Export failed");
            }
            
        } catch (Exception e) {
            logger.error("Error processing export job: {}", jobId, e);
            job.setStatus(JobStatus.FAILED);
            job.setResultMessage("Export failed: " + e.getMessage());
        }
        
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);
        
        return CompletableFuture.completedFuture(null);
    }

    @Async
    protected CompletableFuture<Void> processImportAsync(String jobId, ImportRequest request) {
        IntegrationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IntegrationException("Job not found with ID: " + jobId));
        
        try {
            logger.info("Processing import job: {}", jobId);
            job.setStatus(JobStatus.PROCESSING);
            jobRepository.save(job);
            
            // Call Nasajon API to import data
            boolean success = nasajonApiClient.importData(request);
            
            if (success) {
                job.setStatus(JobStatus.COMPLETED);
                job.setResultMessage("Import completed successfully");
            } else {
                job.setStatus(JobStatus.FAILED);
                job.setResultMessage("Import failed");
            }
            
        } catch (Exception e) {
            logger.error("Error processing import job: {}", jobId, e);
            job.setStatus(JobStatus.FAILED);
            job.setResultMessage("Import failed: " + e.getMessage());
        }
        
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);
        
        return CompletableFuture.completedFuture(null);
    }

    @Async
    protected CompletableFuture<Void> processFileImportAsync(String jobId, MultipartFile file, String type) {
        IntegrationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IntegrationException("Job not found with ID: " + jobId));
        
        try {
            logger.info("Processing file import job: {}", jobId);
            job.setStatus(JobStatus.PROCESSING);
            jobRepository.save(job);
            
            // Process file based on type
            boolean success = nasajonApiClient.importFromFile(file, type);
            
            if (success) {
                job.setStatus(JobStatus.COMPLETED);
                job.setResultMessage("File import completed successfully");
            } else {
                job.setStatus(JobStatus.FAILED);
                job.setResultMessage("File import failed");
            }
            
        } catch (Exception e) {
            logger.error("Error processing file import job: {}", jobId, e);
            job.setStatus(JobStatus.FAILED);
            job.setResultMessage("File import failed: " + e.getMessage());
        }
        
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);
        
        return CompletableFuture.completedFuture(null);
    }
}
[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/service/NasajonApiClient.java"
package com.example.nasajonintegration.service;

import com.example.nasajonintegration.dto.ExportRequest;
import com.example.nasajonintegration.dto.ImportRequest;
import org.springframework.web.multipart.MultipartFile;

public interface NasajonApiClient {
    boolean exportData(ExportRequest request);
    boolean importData(ImportRequest request);
    boolean importFromFile(MultipartFile file, String type);
}
[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/service/impl/NasajonApiClientImpl.java"
package com.example.nasajonintegration.service.impl;

import com.example.nasajonintegration.config.NasajonApiConfig;
import com.example.nasajonintegration.dto.ExportRequest;
import com.example.nasajonintegration.dto.ImportRequest;
import com.example.nasajonintegration.exception.IntegrationException;
import com.example.nasajonintegration.service.NasajonApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;

@Service
public class NasajonApiClientImpl implements NasajonApiClient {

    private static final Logger logger = LoggerFactory.getLogger(NasajonApiClientImpl.class);

    private final RestTemplate restTemplate;
    private final NasajonApiConfig apiConfig;
    private final ObjectMapper objectMapper;

    @Autowired
    public NasajonApiClientImpl(RestTemplate restTemplate, NasajonApiConfig apiConfig, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean exportData(ExportRequest request) {
        try {
            logger.info("Calling Nasajon API to export data: {}", request);
            
            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<ExportRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    apiConfig.getBaseUrl() + "/export",
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            
            logger.info("Export API response: {}", response.getStatusCode());
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            logger.error("Error calling Nasajon export API", e);
            throw new IntegrationException("Failed to export data to Nasajon: " + e.getMessage());
        }
    }

    @Override
    public boolean importData(ImportRequest request) {
        try {
            logger.info("Calling Nasajon API to import data: {}", request);
            
            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<ImportRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    apiConfig.getBaseUrl() + "/import",
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            
            logger.info("Import API response: {}", response.getStatusCode());
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            logger.error("Error calling Nasajon import API", e);
            throw new IntegrationException("Failed to import data from Nasajon: " + e.getMessage());
        }
    }

    @Override
    public boolean importFromFile(MultipartFile file, String type) {
        try {
            logger.info("Calling Nasajon API to import from file: {}, type: {}", file.getOriginalFilename(), type);
            
            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", createFileResource(file));
            body.add("type", type);
            
            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    apiConfig.getBaseUrl() + "/import/file",
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            
            logger.info("File import API response: {}", response.getStatusCode());
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            logger.error("Error calling Nasajon file import API", e);
            throw new IntegrationException("Failed to import file to Nasajon: " + e.getMessage());
        }
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiConfig.getApiKey());
        headers.set("X-Client-Id", apiConfig.getClientId());
        return headers;
    }

    private HttpEntity<byte[]> createFileResource(MultipartFile file) throws IOException {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(file.getContentType()));
        fileHeaders.setContentDispositionFormData("file", file.getOriginalFilename());
        return new HttpEntity<>(file.getBytes(), fileHeaders);
    }
}
[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/config/NasajonApiConfig.java"
package com.example.nasajonintegration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NasajonApiConfig {

    @Value("${nasajon.api.base-url}")
    private String baseUrl;

    @Value("${nasajon.api.api-key}")
    private String apiKey;

    @Value("${nasajon.api.client-id}")
    private String clientId;

    @Value("${nasajon.api.timeout:30000}")
    private int timeout;

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getClientId() {
        return clientId;
    }

    public int getTimeout() {
        return timeout;
    }
}
[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/config/AppConfig.java"
package com.example.nasajonintegration.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(NasajonApiConfig apiConfig) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(apiConfig.getTimeout());
        factory.setReadTimeout(apiConfig.getTimeout());
        return new RestTemplate(factory);
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("NasajonIntegration-");
        executor.initialize();
        return executor;
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Nasajon Integration API")
                        .version("1.0.0")
                        .description("API for integrating with Nasajon systems"));
    }
}
[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/dto/ExportRequest.java"
package com.example.nasajonintegration.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ExportRequest {
    private String entityType;
    private List<String> entityIds;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
    private Map<String, Object> filters;
    private String format;
    private boolean includeRelated;

    // Getters and setters
    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public List<String> getEntityIds() {
        return entityIds;
    }

    public void setEntityIds(List<String> entityIds) {
        this.entityIds = entityIds;
    }

    public LocalDateTime getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDateTime fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDateTime getToDate() {
        return toDate;
    }

    public void setToDate(LocalDateTime toDate) {
        this.toDate = toDate;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean isIncludeRelated() {
        return includeRelated;
    }

    public void setIncludeRelated(boolean includeRelated) {
        this.includeRelated = includeRelated;
    }

    @Override
    public String toString() {
        return "ExportRequest{" +
                "entityType='" + entityType + '\'' +
                ", entityIds=" + entityIds +
                ", fromDate=" + fromDate +
                ", toDate=" + toDate +
                ", filters=" + filters +
                ", format='" + format + '\'' +
                ", includeRelated=" + includeRelated +
                '}';
    }
}
[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/dto/ImportRequest.java"
package com.example.nasajonintegration.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class ImportRequest {
    private String entityType;
    private String sourceType;
    private String sourceId;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
    private Map<String, Object> filters;
    private boolean overwriteExisting;
    private boolean validateOnly;

    // Getters and setters
    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public LocalDateTime getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDateTime fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDateTime getToDate() {
        return toDate;
    }

    public void setToDate(LocalDateTime toDate) {
        this.toDate = toDate;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters;
    }

    public boolean isOverwriteExisting() {
        return overwriteExisting;
    }

    public void setOverwriteExisting(boolean overwriteExisting) {
        this.overwriteExisting = overwriteExisting;
    }

    public boolean isValidateOnly() {
        return validateOnly;
    }

    public void setValidateOnly(boolean validateOnly) {
        this.validateOnly = validateOnly;
    }

    @Override
    public String toString() {
        return "ImportRequest{" +
                "entityType='" + entityType + '\'' +
                ", sourceType='" + sourceType + '\'' +
                ", sourceId='" + sourceId + '\'' +
                ", fromDate=" + fromDate +
                ", toDate=" + toDate +
                ", filters=" + filters +
                ", overwriteExisting=" + overwriteExisting +
                ", validateOnly=" + validateOnly +
                '}';
    }
}
[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/dto/IntegrationResponse.java"
package com.example.nasajonintegration.dto;

public class IntegrationResponse {
    private String jobId;
    private String status;
    private String message;
    private String completedAt;

    public IntegrationResponse() {
    }

    public IntegrationResponse(String jobId, String status, String message) {
        this.jobId = jobId;
        this.status = status;
        this.message = message;
    }

    public IntegrationResponse(String jobId, String status, String message, String completedAt) {
        this.jobId = jobId;
        this.status = status;
        this.message = message;
        this.completedAt = completedAt;
    }

    // Getters and setters
    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }
}
[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/model/IntegrationJob.java"
package com.example.nasajonintegration.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "integration_jobs")
public class IntegrationJob {

    @Id
    private String jobId;
    
    @Column(nullable = false)
    private String type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;
    
    @Column(columnDefinition = "TEXT")
    private String requestData;
    
    @Column(columnDefinition = "TEXT")
    private String resultData;
    
    @Column(columnDefinition = "TEXT")
    private String resultMessage;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime startedAt;
    
    private LocalDateTime completedAt;

    // Getters and setters
    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getRequestData() {
        return requestData;
    }

    public void setRequestData(String requestData) {
        this.requestData = requestData;
    }

    public String getResultData() {
        return resultData;
    }

    public void setResultData(String resultData) {
        this.resultData = resultData;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/model/JobStatus.java"
package com.example.nasajonintegration.model;

public enum JobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}
[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/repository/IntegrationJobRepository.java"
package com.example.nasajonintegration.repository;

import com.example.nasajonintegration.model.IntegrationJob;
import com.example.nasajonintegration.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IntegrationJobRepository extends JpaRepository<IntegrationJob, String> {
    List<IntegrationJob> findByStatus(JobStatus status);
    List<IntegrationJob> findByTypeAndStatus(String type, JobStatus status);
    List<IntegrationJob> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/exception/IntegrationException.java"
package com.example.nasajonintegration.exception;

public class IntegrationException extends RuntimeException {
    
    public IntegrationException(String message) {
        super(message);
    }
    
    public IntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/exception/GlobalExceptionHandler.java"
package com.example.nasajonintegration.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IntegrationException.class)
    public ResponseEntity<Map<String, String>> handleIntegrationException(IntegrationException ex) {
        logger.error("Integration exception occurred: {}", ex.getMessage());
        
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage());
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        logger.error("File size limit exceeded: {}", ex.getMessage());
        
        Map<String, String> response = new HashMap<>();
        response.put("error", "File size exceeds the maximum allowed limit");
        
        return new ResponseEntity<>(response, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        
        Map<String, String> response = new HashMap<>();
        response.put("error", "An unexpected error occurred. Please try again later.");
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
[V0_FILE]java:file="src/main/resources/application.yml"
server:
  port: 8080
  servlet:
    context-path: /

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/nasajon_integration
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    show-sql: false
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

nasajon:
  api:
    base-url: https://api.nasajon.com/v1
    api-key: ${NASAJON_API_KEY}
    client-id: ${NASAJON_CLIENT_ID}
    timeout: 30000

logging:
  level:
    root: INFO
    com.example.nasajonintegration: DEBUG
    org.springframework.web: INFO
    org.hibernate: INFO

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: method
[V0_FILE]java:file="src/main/java/com/example/nasajonintegration/scheduler/IntegrationJobScheduler.java"
package com.example.nasajonintegration.scheduler;

import com.example.nasajonintegration.model.IntegrationJob;
import com.example.nasajonintegration.model.JobStatus;
import com.example.nasajonintegration.repository.IntegrationJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class IntegrationJobScheduler {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationJobScheduler.class);

    private final IntegrationJobRepository jobRepository;

    @Autowired
    public IntegrationJobScheduler(IntegrationJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void cleanupStuckJobs() {
        logger.info("Running job cleanup task");
        
        // Find jobs that have been in PROCESSING state for more than 1 hour
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        
        List<IntegrationJob> stuckJobs = jobRepository.findByStatus(JobStatus.PROCESSING);
        
        for (IntegrationJob job : stuckJobs) {
            if (job.getStartedAt() != null && job.getStartedAt().isBefore(oneHourAgo)) {
                logger.warn("Found stuck job: {}, marking as FAILED", job.getJobId());
                job.setStatus(JobStatus.FAILED);
                job.setResultMessage("Job timed out after processing for more than 1 hour");
                job.setCompletedAt(LocalDateTime.now());
                jobRepository.save(job);
            }
        }
    }

    @Scheduled(cron = "0 0 0 * * *") // Run at midnight every day
    public void purgeOldJobs() {
        logger.info("Running job purge task");
        
        // Find completed jobs older than 30 days
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        
        List<IntegrationJob> oldJobs = jobRepository.findByCreatedAtBetween(
                LocalDateTime.MIN, 
                thirtyDaysAgo
        );
        
        oldJobs.removeIf(job -> job.getStatus() != JobStatus.COMPLETED && job.getStatus() != JobStatus.FAILED);
        
        if (!oldJobs.isEmpty()) {
            logger.info("Purging {} old jobs", oldJobs.size());
            jobRepository.deleteAll(oldJobs);
        }
    }
}