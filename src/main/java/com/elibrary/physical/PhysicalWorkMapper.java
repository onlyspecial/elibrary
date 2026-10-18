package com.elibrary.physical;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PhysicalWorkMapper {

    PhysicalWork findActiveById(@Param("id") Long id);

    PhysicalWork findById(@Param("id") Long id);

    List<PhysicalWork> searchActive(@Param("keyword") String keyword,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    long countActive(@Param("keyword") String keyword);

    List<PhysicalWork> searchAll(@Param("keyword") String keyword,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    long countAll(@Param("keyword") String keyword);

    PhysicalWork findActiveByTitleAndAuthor(@Param("title") String title,
                                            @Param("author") String author);

    int insert(PhysicalWork work);

    int update(PhysicalWork work);

    int softDelete(@Param("id") Long id);
}
