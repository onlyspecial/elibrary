package com.elibrary.borrow;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BorrowRecordMapper {

    int insert(BorrowRecord record);

    BorrowRecord findById(@Param("id") Long id);

    int markReturned(@Param("id") Long id, @Param("returnedAt") LocalDateTime returnedAt);

    List<BorrowRecordView> listOpenAndRecentByUserId(@Param("userId") Long userId);
}
