-- 推广用户学员类型（用户端只读展示）
ALTER TABLE `promotion_user`
    ADD COLUMN `student_type` TINYINT NOT NULL DEFAULT 0 COMMENT '学员类型：0基础用户 1基础学员' AFTER `wechat_id`;
