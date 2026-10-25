.orElseThrow(...)
這是 Java Optional 類別的方法。

findByAccountAndStatus 的回傳型態是 Optional<Member>。意思是：「可能找得到 Member，也可能找不到（是空的）」。

.orElseThrow() 的意思是：如果 Optional 裡面有資料，就把 Member 物件拿出來用；如果裡面是空的（找不到符合的會員），就會執行括號內的 Lambda 表達式來拋出例外。