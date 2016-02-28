# AndroidViewGenerator
在[ButterKnife](https://github.com/JakeWharton/butterknife)这样强大的注解库出来之后，使用注解进行UI开发已经非常普遍。但考虑到效率、学习成本等问题，findViewById方式仍然是不错的选择。

本项目针对日常开发中遇到的UI相关的繁琐操作进行自动化实现，降低开发者在这方面所需要的精力。

###主要功能
1. 支持为Activity、Fragment以及任意类(比如自定义View)从Layout文件生成View实例并初始化；
2. 支持为Adapter生成ViewHolder模板；
3. 支持为View添加监听；
4. 支持增量式修改(考虑到实例化的View会被使用，因此不支持View删除)；

###演示
![AndroidViewGenerator演示](resources/demoB.gif)
加载不出来的可以看这个 [__链接__](http://7xktd8.com1.z0.glb.clouddn.com/demoB.gif)。

###安装
1. 从[这里](https://plugins.jetbrains.com/plugin/8219?pr=)下载，选择Android Studio -> Preferences -> Plugins ->  Install plugin from disk... -> 选择下载的jar包 -> 点击OK，重启即可；
2. 选择Android Studio -> Preferences -> Plugins -> Browse repositories... -> 搜索 "Android View Generator"，安装即可。


#TODO
1. 变量、实例化增量式修改； —— Done
2. 监听、View增加增量式修改；—— Done
3. 支持ViewHolder的生成；—— Done

#感谢
本项目基于 [__android-butterknife-zelezny__](https://github.com/avast/android-butterknife-zelezny) 改造。因此特别感谢：@Avast。