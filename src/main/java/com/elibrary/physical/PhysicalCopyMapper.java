package com.elibrary.physical;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PhysicalCopyMapper {

    PhysicalCopy findFirstAvailableByWorkId(@Param("workId") Long workId);

    PhysicalCopy findById(@Param("id") Long id);

    PhysicalCopy findByIdForUpdate(@Param("id") Long id);

    int countByWorkId(@Param("workId") Long workId);

    int countAvailableByWorkId(@Param("workId") Long workId);

    List<PhysicalCopy> listByWorkId(@Param("workId") Long workId);

    int updateStatus(@Param("id") Long id, @Param("status") CopyStatus status);

    int insert(PhysicalCopy copy);

    int softDelete(@Param("id") Long id);
}
