<script>
export default {}
</script>

<style>
page {
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #f5f6fa;
  color: #111111;
  font-family: "PingFang SC", sans-serif;
  font-size: 17px;
}

view,
image,
swiper,
swiper-item,
scroll-view,
text {
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
  line-height: 1;
  font-size: 17px;
}

button::after {
  border: 0;
}

/* 占位图通用样式 */
.placeholder-image {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e8edf5, #d5dbe8);
}

.placeholder-image--warm {
  background: linear-gradient(135deg, #fff3e0, #ffe0b2);
}

.placeholder-image--cool {
  background: linear-gradient(135deg, #e3f2fd, #bbdefb);
}

.placeholder-image--nature {
  background: linear-gradient(135deg, #e8f5e9, #c8e6c9);
}

.placeholder-image__text {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.3);
  font-weight: 600;
}
</style>
