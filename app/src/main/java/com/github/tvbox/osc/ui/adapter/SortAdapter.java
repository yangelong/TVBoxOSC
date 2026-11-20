package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
public class SortAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {
    public SortAdapter() {
        super(R.layout.item_home_sort, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        helper.setText(R.id.tvTitle, item.name);
        View itemView = helper.itemView;
        TextView title = helper.getView(R.id.tvTitle);
        // 「换片源」是固定入口，背景长期高亮，和普通分类区分开
        boolean isSourceEntry = item.id != null && item.id.equals("my0");
        if (isSourceEntry) {
            itemView.setBackgroundResource(R.drawable.button_home_sort_source);
            if (title != null) {
                title.setTextColor(0xFFFFFFFF);
            }
        } else {
            itemView.setBackgroundResource(R.drawable.button_home_sort_focus);
            if (title != null) {
                title.setTextColor(0xBBFFFFFF);
            }
        }
        // 该分类加载后没有任何数据，把它彻底收成 0 宽并去掉焦点：后面的分类会顶上来，不留空位
        ViewGroup.LayoutParams params = itemView.getLayoutParams();
        if (item.hidden) {
            itemView.setVisibility(View.GONE);
            itemView.setFocusable(false);
            itemView.setClickable(false);
            if (params != null && params.width != 0) {
                params.width = 0;
                itemView.setLayoutParams(params);
            }
            return;
        }
        if (params != null && params.width == 0) {
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            itemView.setLayoutParams(params);
        }
        itemView.setVisibility(View.VISIBLE);
        itemView.setFocusable(true);
        itemView.setClickable(true);
    }
}
