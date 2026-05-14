package xiaozhi.modules.appv2.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.appv2.entity.V2TaskEntity;

@Mapper
public interface V2TaskDao extends BaseMapper<V2TaskEntity> {
}
