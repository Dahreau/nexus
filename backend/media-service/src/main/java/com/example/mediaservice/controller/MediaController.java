package com.example.mediaservice.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.example.mediaservice.model.Media;
import com.example.mediaservice.repository.MediaRepository;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final MediaRepository repo;
    private final Path uploadDir;

    public MediaController(MediaRepository repo) throws IOException {
        this.repo = repo;
        String dir = System.getenv("MEDIA_UPLOAD_DIR");
        this.uploadDir = Paths.get((dir != null && !dir.isBlank()) ? dir : "uploads");
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam("productId") String productId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_SELLER"))) {
                return ResponseEntity.status(403).body(Map.of("error", "Only sellers can upload media"));
            }

            // validate file
            if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Empty file"));
            if (file.getSize() > 2L * 1024 * 1024) return ResponseEntity.badRequest().body(Map.of("error", "File too large (max 2MB)"));
            String contentType = file.getContentType();
            if (contentType == null || !(contentType.equalsIgnoreCase("image/png") || contentType.equalsIgnoreCase("image/jpeg") || contentType.equalsIgnoreCase("image/gif"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unsupported file type"));
            }

            String original = StringUtils.cleanPath(file.getOriginalFilename());
            String ext = "";
            int i = original.lastIndexOf('.');
            if (i >= 0) ext = original.substring(i);
            String filename = UUID.randomUUID().toString() + ext;
            Path target = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), target);
            String mediaBase = System.getenv("MEDIA_SERVICE_URL");
            if (mediaBase == null || mediaBase.isBlank()) mediaBase = "http://localhost:8083";
            String publicPath = mediaBase + "/api/media/file/" + filename;
            Media m = new Media(publicPath, productId);
            repo.save(m);

            // Try to notify product-service to append this media id to the product's imageIds.
            try {
                String internalToken = System.getenv("INTERNAL_TOKEN");
                if (internalToken != null && !internalToken.isBlank()) {
                    RestTemplate rt = new RestTemplate();
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("X-Internal-Token", internalToken);
                    Map<String, String> body = Map.of("mediaId", m.getId());
                    HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
                    String productBase = System.getenv("PRODUCT_SERVICE_URL");
                    if (productBase == null || productBase.isBlank()) productBase = "http://product-service:8082";
                    // best-effort: we don't fail the upload if this internal call fails
                    rt.postForEntity(productBase + "/api/products/" + productId + "/images", request, String.class);
                }
            } catch (Exception ex) {
                // ignore - upload itself succeeded, but we couldn't sync product imageIds
            }

            return ResponseEntity.ok(m);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed", "detail", e.getMessage()));
        }
    }

    @GetMapping("/product/{productId}")
    public List<Media> byProduct(@PathVariable String productId) {
        List<Media> list = repo.findByProductId(productId);
        // normalize stored imagePath to public URLs if needed
        String mediaBase = System.getenv("MEDIA_SERVICE_URL");
        if (mediaBase == null || mediaBase.isBlank()) mediaBase = "http://localhost:8083";
        for (Media m : list) {
            String p = m.getImagePath();
            if (p == null) continue;
            if (!p.startsWith("http://") && !p.startsWith("https://")) {
                // assume original stored a filesystem path like "uploads/..." or absolute path; extract filename
                try {
                    Path pp = Paths.get(p);
                    String fname = pp.getFileName().toString();
                    m.setImagePath(mediaBase + "/api/media/file/" + fname);
                } catch (Exception ex) {
                    // fallback: leave as-is
                }
            }
        }
        return list;
    }

    @GetMapping("/file/{filename:.+}")
    public ResponseEntity<?> file(@PathVariable String filename) {
        try {
            Path f = uploadDir.resolve(filename).normalize();
            if (!Files.exists(f)) return ResponseEntity.notFound().build();
            String contentType = Files.probeContentType(f);
            if (contentType == null) contentType = "application/octet-stream";
            byte[] data = Files.readAllBytes(f);
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(contentType)).body(data);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Could not read file"));
        }
    }
}