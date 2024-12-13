package com.td.springbootinit.mapper;

import com.td.springbootinit.model.entity.Chart;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
* @author 86147
* @description 针对表【chart(图表信息表)】的数据库操作Mapper
* @createDate 2024-04-26 11:44:54
* @Entity generator.domain.Chart
*/

public interface ChartMapper extends BaseMapper<Chart> {
    @MapKey("key")
    List<Map<String,Object>> queryChartdata(String querySql);

}




