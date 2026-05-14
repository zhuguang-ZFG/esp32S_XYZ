package xiaozhi.modules.appv2.service;

/**
 * 微信小程序登录网关。把 jscode2session 调用抽出来，方便测试与 dev 模拟。
 */
public interface WechatLoginGateway {

    /**
     * 用客户端临时登录 code 换取微信侧身份。
     *
     * @param code 微信 wx.login 返回的 code
     * @return 微信会话信息（unionid/openid）
     */
    WechatSession exchange(String code);

    final class WechatSession {
        private final String unionid;
        private final String openid;

        public WechatSession(String unionid, String openid) {
            this.unionid = unionid;
            this.openid = openid;
        }

        public String getUnionid() {
            return unionid;
        }

        public String getOpenid() {
            return openid;
        }
    }
}
