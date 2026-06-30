package org.example.xyyx.service;

public enum GlobalPhoneDisplayPolicyMode {
    /** "手机号隐私设置" 开关 ON：点任意可见订单小眼睛后，本次登录内所有可见订单不再隐藏。 */
    CLICK_TO_SESSION_VISIBLE,
    /** "手机号隐私设置" 开关 OFF：点小眼睛只揭示该一条订单，限时 300 秒，到期后端失效。 */
    SINGLE_ORDER_TIMED_REVEAL,
    /** @deprecated 旧"仅脱敏"，已从设置 UI 移除；仅保留以兼容历史 DB 行/测试，新逻辑不再写入。 */
    @Deprecated
    MASKED_ONLY
}
