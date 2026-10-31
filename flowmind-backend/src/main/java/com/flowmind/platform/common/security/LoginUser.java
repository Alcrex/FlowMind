package com.flowmind.platform.common.security;
public record LoginUser(Long id, String username, String realName, String roleType, String tokenId) {}
