<script>
export default {}
</script>

<style>
page {
  width: 100%;
  height: 100%;
  overflow-x: hidden;
  background: #f5f6fa;
  color: #111111;
  font-family: "PingFang SC", "Helvetica Neue", sans-serif;
  font-size: 18px;
  line-height: 1.55;
  -webkit-font-smoothing: antialiased;
}

view, image, swiper, swiper-item, scroll-view, text {
  box-sizing: border-box;
}

button {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  outline: none;
  background: transparent;
  box-shadow: none;
  line-height: 1.4;
  font-size: 18px;
  font-family: "PingFang SC", "Helvetica Neue", sans-serif;
}

button::after { border: 0; }

/* 通用文本防溢出：长文本省略号，不超出方框 */
text, .text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
}

view { overflow-x: hidden; }

/* 占位图通用样式 */
.placeholder-image {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e8ebef 0%, #d8dde3 100%);
}
.placeholder-image--warm { background: linear-gradient(135deg, #fff3e0, #ffe0b2); }
.placeholder-image--cool { background: linear-gradient(135deg, #e3f2fd, #bbdefb); }
.placeholder-image--nature { background: linear-gradient(135deg, #e8f5e9, #c8e6c9); }
.placeholder-image__text {
  font-size: 16px;
  color: rgba(0, 0, 0, 0.3);
  font-weight: 600;
}

/* 通用按钮样式 */
.btn-primary {
  display: flex; align-items: center; justify-content: center;
  min-height: 48px; padding: 0 24px;
  border-radius: 12px;
  background: linear-gradient(140deg, #fe5f02, #ff913f);
  color: #fff; font-size: 18px; font-weight: 700;
  box-shadow: 0 4px 12px rgba(254, 95, 2, 0.25);
}
.btn-ghost {
  display: flex; align-items: center; justify-content: center;
  min-height: 48px; padding: 0 24px;
  border: 2px solid #e0e0e0; border-radius: 12px;
  background: #fff; color: #111; font-size: 18px; font-weight: 600;
}
</style>
