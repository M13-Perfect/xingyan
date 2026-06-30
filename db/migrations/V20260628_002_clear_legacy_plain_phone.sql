-- Clear legacy plaintext phone after encrypted phone fields have been backfilled.
-- This is intentionally separate from V20260628_001: run V001, backfill phone_* with
-- the production key material, verify full phone privacy coverage, then run this.
SET NAMES utf8mb4;

UPDATE `survey`
SET `phone` = NULL
WHERE `phone` IS NOT NULL
  AND `phone` <> ''
  AND `phone_hash` IS NOT NULL
  AND `phone_suffix4_hash` IS NOT NULL
  AND `phone_mask` IS NOT NULL
  AND `phone_mask` <> ''
  AND `phone_ciphertext` IS NOT NULL
  AND `phone_iv` IS NOT NULL
  AND `phone_tag` IS NOT NULL
  AND `phone_enc_key_version` IS NOT NULL
  AND `phone_hash_key_version` IS NOT NULL;
