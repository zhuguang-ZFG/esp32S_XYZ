<script setup lang="ts">
import { useUltrasonicAudio } from '../composables/useUltrasonicAudio'
import { t } from '@/i18n'

// 类型定义
interface WiFiNetwork {
  ssid: string
  rssi: number
  authmode: number
  channel: number
}

// Props
interface Props {
  selectedNetwork: WiFiNetwork | null
  password: string
}

const props = defineProps<Props>()

// AFSK 音频生成 + 播放（P3.1 提取到 useUltrasonicAudio + afskAudio）
const {
  generating,
  playing,
  audioGenerated,
  autoLoop,
  canGenerate,
  audioLengthText,
  generateAndPlay,
  playAudio,
  stopAudio,
} = useUltrasonicAudio(() => props.selectedNetwork, () => props.password)
</script>

<template>
  <view class="ultrasonic-config">
    <!-- 选中的网络信息 -->
    <view v-if="props.selectedNetwork" class="selected-network">
      <view class="network-info">
        <view class="network-name">
          {{ t('deviceConfig.selectedNetwork') }}: {{ props.selectedNetwork.ssid }}
        </view>
        <view class="network-details">
          <text class="network-signal">
            {{ t('deviceConfig.signal') }}: {{ props.selectedNetwork.rssi }}dBm
          </text>
          <text class="network-security">
            {{ props.selectedNetwork.authmode === 0 ? t('deviceConfig.openNetwork') : t('deviceConfig.encryptedNetwork') }}
          </text>
        </view>
        <view v-if="props.password" class="network-password">
          {{ t('deviceConfig.password') }}: {{ '*'.repeat(props.password.length) }}
        </view>
      </view>
    </view>

    <!-- 超声波配网操作 -->
    <view class="submit-section">
      <wd-button
        type="primary"
        size="large"
        block
        :loading="generating"
        :disabled="!canGenerate"
        @click="generateAndPlay"
      >
        {{ generating ? t('deviceConfig.generating') : `🎵 ${t('deviceConfig.generateAndPlaySoundWave')}` }}
      </wd-button>

      <wd-button
        v-if="audioGenerated"
        type="success"
        size="large"
        block
        :loading="playing"
        @click="playAudio"
      >
        {{ playing ? t('deviceConfig.playing') : `🔊 ${t('deviceConfig.playSoundWave')}` }}
      </wd-button>

      <wd-button
        v-if="playing"
        type="warning"
        size="large"
        block
        @click="stopAudio"
      >
        ⏹️ {{ t('deviceConfig.stopPlaying') }}
      </wd-button>
    </view>

    <!-- 音频控制选项 -->
    <view v-if="audioGenerated" class="audio-options">
      <view class="option-item">
        <wd-checkbox v-model="autoLoop">
          {{ t('deviceConfig.autoLoopPlaySoundWave') }}
        </wd-checkbox>
      </view>
    </view>

    <!-- 音频播放器 -->
    <view v-if="audioGenerated" class="audio-player">
      <view class="player-info">
        <text class="audio-title">
          {{ t('deviceConfig.configAudioFile') }}
        </text>
        <text class="audio-duration">
          {{ t('deviceConfig.duration') }}: {{ audioLengthText }}
        </text>
      </view>
    </view>

    <!-- 使用说明 -->
    <view class="help-section">
      <view class="help-title">
        {{ t('deviceConfig.ultrasonicConfigInstructions') }}
      </view>
      <view class="help-content">
        <text class="help-item">
          1. {{ t('deviceConfig.ensureWifiNetworkSelectedAndPasswordEntered') }}
        </text>
        <text class="help-item">
          2. {{ t('deviceConfig.clickGenerateAndPlaySoundWave') }}
        </text>
        <text class="help-item">
          3. {{ t('deviceConfig.bringPhoneCloseToDlcDevice') }}
        </text>
        <text class="help-item">
          4. {{ t('deviceConfig.duringAudioPlaybackDlcWillReceive') }}
        </text>
        <text class="help-item">
          5. {{ t('deviceConfig.afterConfigSuccessDeviceWillConnect') }}
        </text>
        <text class="help-tip">
          {{ t('deviceConfig.usesAfskModulation') }}
        </text>
        <text class="help-tip">
          {{ t('deviceConfig.ensureModeratePhoneVolume') }}
        </text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.ultrasonic-config {
  padding: 20rpx 0;
}

.selected-network {
  margin-bottom: 32rpx;
}

.network-info {
  padding: 24rpx;
  background-color: var(--bg2);
  border: 1rpx solid var(--accent);
  border-radius: 16rpx;
}

.network-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8rpx;
}

.network-details {
  display: flex;
  gap: 24rpx;
  margin-bottom: 8rpx;
}

.network-signal,
.network-security {
  font-size: 24rpx;
  color: var(--muted);
}

.network-password {
  font-size: 24rpx;
  color: var(--muted);
}

.submit-section {
  margin-bottom: 32rpx;
}

.submit-section .wd-button {
  margin-bottom: 16rpx;
}

.submit-section .wd-button:last-child {
  margin-bottom: 0;
}

.audio-options {
  margin-bottom: 32rpx;
  padding: 24rpx;
  background-color: var(--bg2);
  border-radius: 16rpx;
  border: 1rpx solid var(--border);
}

.option-item {
  font-size: 28rpx;
}

.audio-player {
  margin-bottom: 32rpx;
  padding: 24rpx;
  background-color: var(--bg2);
  border: 1rpx solid var(--accent);
  border-radius: 16rpx;
}

.player-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.audio-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text);
}

.audio-duration {
  font-size: 24rpx;
  color: var(--muted);
}

.help-section {
  padding: 32rpx 24rpx;
  background-color: var(--bg2);
  border-radius: 16rpx;
  border: 1rpx solid var(--border);
}

.help-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 20rpx;
}

.help-content {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.help-item {
  font-size: 24rpx;
  color: var(--muted);
  line-height: 1.5;
}

.help-tip {
  font-size: 24rpx;
  color: var(--accent);
  font-weight: 500;
  margin-top: 8rpx;
}
</style>
