-- 扩展 api_key.secret_prefix 字段长度，从 24 到 64，以存储完整的 32 位凭证
ALTER TABLE "public"."api_key" ALTER COLUMN "secret_prefix" TYPE varchar(32);
