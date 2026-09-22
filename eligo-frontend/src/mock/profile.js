/**
 * 个人资料模块 mock 数据
 * 仅在 VITE_PROFILE_DATA_MODE=mock 时使用，不作为 API 失败回退。
 */

export const mockProfile = {
  userId: "1900000000000000201",
  nickname: "小艾",
  avatar: null,
  birthDate: "2000-01-02",
  gender: "FEMALE",
  region: {
    provinceCode: "44",
    provinceName: "广东省",
    cityCode: "4401",
    cityName: "广州市",
    districtCode: "440106",
    districtName: "天河区",
  },
  email: "xiaoyi@example.com",
  bio: "周末喜欢徒步和看展",
  interestTags: [
    { interestTagId: "1900000000000001001", code: "OUTDOOR", name: "户外", sortOrder: 10 },
  ],
  phoneBinding: {
    bound: true,
    maskedPhone: "138****8000",
  },
  profileCompleted: true,
  completedAt: "2026-08-04T08:00:00Z",
  nextNicknameChangeAt: null,
}

export const mockInterestTags = [
  { interestTagId: "1900000000000001001", code: "OUTDOOR", name: "户外", sortOrder: 10 },
  { interestTagId: "1900000000000001002", code: "SPORTS", name: "运动", sortOrder: 20 },
  { interestTagId: "1900000000000001003", code: "FOOD", name: "美食", sortOrder: 30 },
  { interestTagId: "1900000000000001004", code: "TRAVEL", name: "旅行", sortOrder: 40 },
  { interestTagId: "1900000000000001005", code: "MUSIC", name: "音乐", sortOrder: 50 },
  { interestTagId: "1900000000000001006", code: "MOVIE", name: "电影", sortOrder: 60 },
  { interestTagId: "1900000000000001007", code: "READING", name: "阅读", sortOrder: 70 },
  { interestTagId: "1900000000000001008", code: "PHOTOGRAPHY", name: "摄影", sortOrder: 80 },
  { interestTagId: "1900000000000001009", code: "GAMING", name: "游戏", sortOrder: 90 },
  { interestTagId: "1900000000000001010", code: "PETS", name: "宠物", sortOrder: 100 },
]
