package com.flowmind.platform.common.api;

import java.util.List;

public record PageResult<T>(List<T> records, long total, long pageNum, long pageSize) {}
