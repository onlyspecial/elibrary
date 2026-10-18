package com.elibrary.digital;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DigitalItemMapper {

    DigitalItem findActiveById(@Param("id") Long id);

    DigitalItem findById(@Param("id") Long id);

    List<DigitalItem> searchActive(@Param("keyword") String keyword,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    long countActive(@Param("keyword") String keyword);

    List<DigitalItem> searchAll(@Param("keyword") String keyword,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    long countAll(@Param("keyword") String keyword);

    DigitalItem findActiveByTitleAndAuthor(@Param("title") String title,
                                           @Param("author") String author);

    int insert(DigitalItem item);

    int update(DigitalItem item);

    int softDelete(@Param("id") Long id);
}
