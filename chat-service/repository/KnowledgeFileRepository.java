package com.example.repository;
import com.example.model.KnowledgeBaseFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeFileRepository extends JpaRepository<KnowledgeBaseFile, Long> {

    Optional<KnowledgeBaseFile> findByFileId(String fileId);

    List<KnowledgeBaseFile> findAllByOrderByIdDesc();

    @Modifying
    @Query("DELETE FROM KnowledgeBaseFile k WHERE k.fileId = :fileId")
    void deleteByFileId(@Param("fileId") String fileId);
}

