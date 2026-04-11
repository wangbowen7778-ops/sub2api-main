package com.sub2api.module.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.account.model.entity.Account;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账号 Mapper
 *
 * @author Alibaba Java Code Guidelines
 */
@Mapper
public interface AccountMapper extends BaseMapper<Account> {
}
