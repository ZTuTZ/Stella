/** story-carousel — 故事卡横向轮播：stories [{id,title,tone,favorite,playing}]。
 * 事件：storytap { id }。生成完成页/首页共用。 */
import { CarouselStory } from "../../types/api";

Component({
  properties: {
    stories: { type: Array, value: [] as CarouselStory[] },
  },
  methods: {
    onCardTap(e: WechatMiniprogram.TouchEvent) {
      const id = Number(e.currentTarget.dataset.id);
      this.triggerEvent("storytap", { id });
    },
  },
})
