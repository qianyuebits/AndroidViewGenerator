# AndroidViewGenerator
在[ButterKnife](https://github.com/JakeWharton/butterknife)这样强大的注解库出来之后，使用注解进行UI开发已经非常普遍。但考虑到效率、学习成本等问题，findViewById方式仍然是不错的选择。

本项目针对日常开发中遇到的UI相关的繁琐操作进行自动化实现，降低开发者在这方面所需要的精力。

###主要功能
自动从layout生成native代码，包括声明变量，使用findViewById实例化变量，为View添加监听，自动生成ViewHolder模板代码。

###演示
![AndroidViewGenerator演示](http://7xktd8.com1.z0.glb.clouddn.com/demo.gif)

#TODO
1. 变量、实例化增量式修改； —— Done
2. 监听增量式修改；—— TODO
3. 支持ViewHolder的生成；—— Done

#感谢
本项目基于[android-butterknife-zelezny](https://github.com/avast/android-butterknife-zelezny)改造。因此特别感谢：@Avast。