package com.zouliangbu.server.admin;

import com.zouliangbu.server.common.ApiResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.zouliangbu.server.admin.AdminSupport.badRequest;
import static com.zouliangbu.server.admin.AdminSupport.ok;

/**
 * 图片上传（线路封面、动态配图）。
 *
 * 第一版存本地 {@code ./uploads/}，通过 {@link UploadWebConfig} 映射成 {@code /uploads/**} 静态资源。
 * 换 OSS 时只需替换本类的 {@link #store} 方法体，返回的 URL 语义不变，前端零改动。
 *
 * <b>安全相关（都不是可选项）</b>：
 * - 扩展名白名单，且以<b>声明内容</b>为准重新拼文件名，绝不使用客户端传来的原文件名
 *   （原文件名可能带 {@code ../} 或 {@code .html}，前者能写出目录、后者能变成存储型 XSS）。
 * - 校验真实内容魔数，防止"改后缀传 html"。
 * - 单文件 5MB 上限，且不解析图片内容（不重新编码，避免 ImageIO 解码漏洞拖垮服务）。
 */
@RestController
@RequestMapping("/api/v1/admin/upload")
public class AdminUploadController {

    private static final List<String> ALLOWED_EXT = List.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final Path root;
    private final String publicPrefix;

    public AdminUploadController(@Value("${admin.upload.dir:./uploads}") String dir,
                                 @Value("${admin.upload.public-prefix:/uploads}") String publicPrefix) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        this.publicPrefix = publicPrefix;
    }

    /**
     * 上传一张图，返回可直接写进 coverUrl 的相对路径。
     *
     * 返回相对路径（{@code /uploads/2026-10/xxx.jpg}）而不是带域名的绝对地址：
     * 后台可能在内网 IP、外网域名、localhost 三种地址下访问，写死域名后 APK 里就再也改不掉了。
     */
    @PostMapping
    public ResponseEntity<ApiResponse<UploadResult>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return badRequest("文件为空");
        }
        if (file.getSize() > MAX_BYTES) {
            return badRequest("图片不能超过 5MB");
        }

        String ext = extensionOf(file.getOriginalFilename());
        if (ext == null || !ALLOWED_EXT.contains(ext)) {
            return badRequest("只支持 jpg / jpeg / png / webp");
        }
        try (InputStream in = file.getInputStream()) {
            if (!looksLikeImage(in, ext)) {
                return badRequest("文件内容不是有效图片（扩展名与实际格式不符）");
            }
        } catch (IOException e) {
            return badRequest("读取上传内容失败：" + e.getMessage());
        }

        // 按日期分目录，避免单目录几万个文件拖慢 ls
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        // 文件名完全由服务端生成，与客户端传来的名字无关
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path targetDir = root.resolve(dateDir).normalize();
        if (!targetDir.startsWith(root)) {
            return badRequest("非法路径");
        }
        try {
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(filename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            return badRequest("保存失败：" + e.getMessage());
        }

        String url = publicPrefix + "/" + dateDir + "/" + filename;
        return ok(new UploadResult(url, file.getSize()));
    }

    /** 取小写扩展名；没有扩展名返回 null。 */
    private static String extensionOf(String originalName) {
        if (originalName == null) {
            return null;
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return null;
        }
        return originalName.substring(dot + 1).toLowerCase();
    }

    /**
     * 魔数判断。只看头 12 字节：
     * JPEG = FF D8 FF；PNG = 89 50 4E 47 0D 0A 1A 0A；WEBP = "RIFF"+4字节长度+"WEBP"。
     *
     * 不做完整解码：ImageIO 解码畸形图可能抛 OOM/栈溢出，上传接口不该承担这个风险。
     */
    private static boolean looksLikeImage(InputStream in, String ext) throws IOException {
        byte[] head = new byte[12];
        int n = in.readNBytes(head, 0, 12);
        if (n < 4) {
            return false;
        }
        return switch (ext) {
            case "jpg", "jpeg" -> (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            case "png" -> (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            case "webp" -> n >= 12
                    && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            default -> false;
        };
    }

    public record UploadResult(String url, long size) {}
}
