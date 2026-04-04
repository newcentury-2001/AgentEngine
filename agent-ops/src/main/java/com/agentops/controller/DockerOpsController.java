package com.agentops.controller;

import com.agentops.service.DockerOpsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/ops/docker")
public class DockerOpsController {

    private final DockerOpsService dockerOpsService;

    public DockerOpsController(DockerOpsService dockerOpsService) {
        this.dockerOpsService = dockerOpsService;
    }

    @PostMapping("/init")
    public Map<String, Object> init() {
        return dockerOpsService.initContainers();
    }

    @PostMapping(value = "/upload-sql", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadSql(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "remoteName", required = false) String remoteName
    ) {
        return dockerOpsService.uploadSqlFile(file, remoteName);
    }

    @PostMapping(value = "/upload-script", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadScript(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "remoteName", required = false) String remoteName
    ) {
        return dockerOpsService.uploadScriptFile(file, remoteName);
    }

    @PostMapping("/upload-bundle")
    public Map<String, Object> uploadBundle() {
        return dockerOpsService.uploadBundleFiles();
    }

    @PostMapping("/upload-sql-bundle")
    public Map<String, Object> uploadSqlBundle() {
        return dockerOpsService.uploadSqlBundleFiles();
    }

    @PostMapping("/upload-script-bundle")
    public Map<String, Object> uploadScriptBundle() {
        return dockerOpsService.uploadScriptBundleFiles();
    }
}
