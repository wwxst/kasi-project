-- 推广用户自助资料联系方式
ALTER TABLE `promotion_user`
    ADD COLUMN `wechat_id` VARCHAR(128) DEFAULT NULL COMMENT '微信号' AFTER `email`;
