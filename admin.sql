USE `xyyx_db`;

INSERT INTO `user` (`username`, `password`, `role`)
VALUES ('18007300157', '$2a$10$3Gyun6Md352S6lpRntWuD.wL/jjj1/2kWWs0APmSS3yMP4Wsy7oAC', 'admin')
ON DUPLICATE KEY UPDATE
  `password` = VALUES(`password`),
  `role` = 'admin';
