package com.approval.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("application_comments")
public class ApplicationComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applicationId;

    private Long userId;

    private String content;

    private Long parentId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic(value = "NULL", delval = "NOW()")
    private LocalDateTime deletedAt;
}
