
📝 记事本功能概要

1. 界面展示
   
记事本主界面如下所示：

<img width="465" height="930" alt="image" src="https://github.com/user-attachments/assets/ba9b1eb5-4b58-4c13-b10a-8f53f3972225" />

上方栏按钮功能依次为：

新建笔记

搜索笔记

其他小功能

<img width="447" height="923" alt="image" src="https://github.com/user-attachments/assets/41779436-6cc9-4e07-9b2f-298389bae24b" />

2. 时间显示
   
笔记的更新时间显示在该笔记行右下角。

<img width="459" height="75" alt="image" src="https://github.com/user-attachments/assets/990c8901-2b5f-467c-a932-7f33288f5db0" />

3. 搜索笔记 🔍
   
搜索功能支持对笔记标题和笔记内容进行模糊匹配。

示例一：匹配标题

test1 笔记内容为 1111111

test2 笔记内容为 mkdir

当搜索框输入内容为 test 时，两个笔记都能显示。

当搜索框输入内容为 test2 时，只显示 test2 笔记。

<img width="462" height="479" alt="image" src="https://github.com/user-attachments/assets/b56070c9-11db-4ee0-b44d-1a55c0b0c9c2" /> <img width="473" height="466" alt="image" alt="image" src="https://github.com/user-attachments/assets/e845387a-45e2-45cd-b89c-f69bc5f0e429" />

示例二：匹配内容

搜索框内容为 m 时，因为 test1 笔记标题与内容（1111111）里都没有 m，而 test2 笔记内容为 mkdir（包含 m），所以只显示 test2 笔记。

<img width="463" height="331" alt="image" src="https://github.com/user-attachments/assets/5b98e175-813e-443c-8bea-ab6cd81ecf5d" />

4. 新建笔记 ✨
5. 
新建笔记界面如下所示，上方栏按钮功能从左到右依次为：保存、删除、以及其他小功能。

<img width="460" height="341" alt="image" src="https://github.com/user-attachments/assets/d4eab60d-438f-481a-861c-35f57effaf90" /> 

新建流程：

填写内容。

点击保存，即可新建笔记。

初次新建时，笔记标题默认为内容。

点击笔记后可进入修改界面修改标题。

<img width="451" height="294" alt="image" src="https://github.com/user-attachments/assets/328ec731-27c2-4a44-aba9-b44b29d70080" />

4.1 添加附件

选择需要添加的内容类型。

<img width="417" height="230" alt="image" src="https://github.com/user-attachments/assets/70494089-43c1-4032-bfcf-e899d50df83d" />

附件添加会跳转到 DCIM 文件夹。 

<img width="447" height="544" alt="image" src="https://github.com/user-attachments/assets/26b9f32a-0234-489d-9301-6e45cc7dd314" />

4.2 设置分类

选择设置分类。

输入分类内容后，笔记的分类便会更新。

<img width="405" height="189" alt="image" src="https://github.com/user-attachments/assets/ea480d96-73fc-4c4d-a93b-bc3650c4ad2c" />

5. 部分代码展示 💻
5.1 时间戳的展示
notelist.java 里的 getView 方法：用于获取和设置时间戳视图。

```Java

if (convertView == null || !(convertView.getTag() instanceof NoteHolder)) {
    convertView = mInflater.inflate(R.layout.noteslist_item, parent, false);
    holder = new NoteHolder();

    holder.title = (TextView) convertView.findViewById(android.R.id.text1);
    holder.timestamp = (TextView) convertView.findViewById(R.id.text_timestamp);

    convertView.setTag(holder);
} else {
    holder = (NoteHolder) convertView.getTag();
}
```
notelist_item.xml：定义时间戳 TextView 的布局，设置了右对齐和底部对齐。

```XML

<TextView
    android:id="@+id/text_timestamp"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"

    android:layout_alignParentRight="true"
    android:layout_alignParentBottom="true"
    android:layout_marginBottom="6dp"

    android:textAppearance="?android:attr/textAppearanceSmall"
    android:textColor="#000000"
    android:singleLine="true"
    android:ellipsize="end" />
```
5.2 多选笔记功能
notelist.java 里的 onOptionsItemSelected 方法：处理菜单项点击事件，包括进入/退出多选模式，应用/清除分类等。

```Java

public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.menu_add) {
        startActivityForResult(new Intent(Intent.ACTION_INSERT, getIntent().getData()), ACTIVITY_REQUEST_CODE_EDIT);
        return true;
    } else if (item.getItemId() == R.id.menu_paste) {
        startActivityForResult(new Intent(Intent.ACTION_INSERT, getIntent().getData()), ACTIVITY_REQUEST_CODE_EDIT);
        return true;
    } else if (item.getItemId() == R.id.menu_toggle_multi_select) {
        if (mMultiSelectMode) {
            exitMultiSelect();
        } else {
            enterMultiSelect();
        }
        return true;
    } else if (item.getItemId() == R.id.menu_apply_category) {
        if (mSelectedIds.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_selection, Toast.LENGTH_SHORT).show();
        } else {
            showCategoryDialogForSelection();
        }
        return true;
    } else if (item.getItemId() == R.id.menu_create_category) {
        showCreateCategoryDialog();
        return true;
    } else if (item.getItemId() == R.id.menu_clear_category) {
        if (mSelectedIds.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_selection, Toast.LENGTH_SHORT).show();
        } else {
            clearCategoryForSelection();
        }
        return true;
    }
    return super.onOptionsItemSelected(item);
}
```
6. 仍需要修改精进的部分 ⚠️
   
    6.1  设置分类退出后无法第一时间显示修改分类结果。

    6.2  界面美观度有待提高。

    6.3  功能实现过少，可考虑增加更多实用功能。
