package com.whatsappgroups.interfaces.api

import com.whatsappgroups.infrastructure.storage.S3UploadService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/upload")
class UploadController(private val s3UploadService: S3UploadService) {

    companion object {
        private const val MAX_SIZE = 5L * 1024 * 1024  // 5 MB
    }

    @PostMapping("/image", consumes = ["multipart/form-data"])
    fun uploadImage(
        @AuthenticationPrincipal user: UserDetails,
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<Map<String, String>> {
        require(!file.isEmpty) { "Arquivo vazio" }
        require(file.size <= MAX_SIZE) { "Imagem deve ter no máximo 5 MB" }
        require(file.contentType?.startsWith("image/") == true) {
            "Somente imagens são aceitas (JPEG, PNG, GIF, WEBP)"
        }

        val url = s3UploadService.uploadImage(file, user.username)
        return ResponseEntity.ok(mapOf("url" to url))
    }
}
