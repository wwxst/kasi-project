package com.kasi.backend.promotion.mapper;

import com.kasi.backend.promotion.dto.AdminMediaAccountPageQueryDTO;
import com.kasi.backend.promotion.vo.MediaAccountFilingExportRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MediaAccountFilingExportMapper {
    List<MediaAccountFilingExportRow> findForExport(@Param("query") AdminMediaAccountPageQueryDTO query);
}
