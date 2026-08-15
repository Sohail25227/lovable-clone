package com.aibuilder.lovableclone.workspace.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aibuilder.lovableclone.workspace.entity.ProjectEntity;
import com.aibuilder.lovableclone.workspace.entity.ProjectStatusEnum;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long>{
    List<ProjectEntity> findByOwnerId(Long ownerId);
    Optional<ProjectEntity> findByIdAndOwnerId(Long id, Long ownerId);
    long countByOwnerId(Long ownerId);

    // Compare-and-set: read-then-write do requests ko ek hi status pe pahunchne deta hai,
    // yeh nahi. Affected row count hi jawab hai — 1 matlab is caller ne claim kiya.
    //
    // updatedAt aur version haath se set hote hain: bulk update pe @PreUpdate chalta nahi
    // aur Hibernate version khud nahi badhata. Version na badhana optimistic locking ko
    // chupchaap tod dega, kyunki kahin load hui entity stale hone ke baad bhi stale nahi lagegi
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ProjectEntity p
               set p.status = :status,
                   p.updatedAt = :now,
                   p.version = p.version + 1
             where p.id = :projectId
               and p.ownerId = :ownerId
               and p.status <> :status
            """)
    int compareAndSetStatus(@Param("projectId") Long projectId,
                            @Param("ownerId") Long ownerId,
                            @Param("status") ProjectStatusEnum status,
                            @Param("now") Instant now);
}
