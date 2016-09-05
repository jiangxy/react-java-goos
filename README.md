# react-java-goos

配合[React通用后台](https://github.com/jiangxy/react-antd-admin)使用的一个小工具。

[React通用后台](https://github.com/jiangxy/react-antd-admin)要求后端接口必须符合特定的格式，比如查询接口必须是`/api/xxx/select`、接口的返回必须是`HTTP 200`等。如果每个项目接入的时候都从头写一遍，估计要崩溃了。。。所以我在想能不能减少一些重复的工作。

如果你的后端是java的（基于Spring），这个工具可以帮你快速生成一些模版类。把生成的类copy到自己的工程中并填写自己的逻辑，就可以直接使用通用后台提供的登录/CRUD/导入/导出等功能。

## Quick Start

首先要按照[React通用后台](https://github.com/jiangxy/react-antd-admin)的要求写好querySchema和dataSchema文件，然后直接执行jar文件即可：

`java -jar goos-0.1.0.jar [输入目录] [输出目录]`

输入目录大概是这种结构：
```bash
foolbear:schema $ ls -lh
total 48
-rw-r--r--  1 foolbear  staff   377B  9  5 00:41 test.config.js  // test表的配置文件
-rw-r--r--  1 foolbear  staff   1.5K  9  5 00:41 test.dataSchema.js  // test表的dataSchema
-rw-r--r--  1 foolbear  staff   3.4K  9  5 00:41 test.querySchema.js  // test表的querySchema
-rw-r--r--  1 foolbear  staff   133B  9  5 00:41 testSms.config.js  // testSms表的配置文件
-rw-r--r--  1 foolbear  staff   547B  9  5 00:41 testSms.dataSchema.js
-rw-r--r--  1 foolbear  staff   762B  9  5 00:41 testSms.querySchema.js
```

输出目录的结构：
```bash
foolbear:output $ ls -lh -R
total 16
-rw-r--r--  1 foolbear  staff   2.1K  9  5 15:29 CommonResult.java  // 通用工具类
-rw-r--r--  1 foolbear  staff   1.4K  9  5 15:29 LoginController.java  // 登录相关接口
drwxr-xr-x  5 foolbear  staff   170B  9  5 15:29 test
drwxr-xr-x  5 foolbear  staff   170B  9  5 15:29 testSms

./test:  // test表相关的类
total 32
-rw-r--r--  1 foolbear  staff   7.6K  9  5 15:29 TestController.java  // test表CRUD相关接口
-rw-r--r--  1 foolbear  staff   726B  9  5 15:29 TestQueryVO.java
-rw-r--r--  1 foolbear  staff   269B  9  5 15:29 TestVO.java

./testSms:  // testSms表相关的类
total 32
-rw-r--r--  1 foolbear  staff   7.6K  9  5 15:29 TestSmsController.java
-rw-r--r--  1 foolbear  staff   453B  9  5 15:29 TestSmsQueryVO.java
-rw-r--r--  1 foolbear  staff   317B  9  5 15:29 TestSmsVO.java
```

把生成的类copy到自己的项目中，将Controller类的逻辑填写完整，前端需要的接口就完成了。

更多文档请参考[React通用后台](https://github.com/jiangxy/react-antd-admin)项目。

## 关于goos

我在给这个项目想名字的时候，突然想起了早年玩过的一个游戏[World of Goo](https://zh.wikipedia.org/wiki/%E7%B2%98%E7%B2%98%E4%B8%96%E7%95%8C)。其中goo是一种黏黏糊糊的球状生物，可以搭建各种各样的结构，连接不同的建筑，跨越各种地形。倒是蛮切题的，这个项目也是希望**连接**React前端和java后端嘛。

能力所限，我只能给出java版本的，而且也未必是最优的。希望以后能有更多版本的goos吧。
