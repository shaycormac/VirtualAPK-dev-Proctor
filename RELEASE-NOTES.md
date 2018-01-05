# Release Notes

## 0.9.0
开源的第一个版本，支持了几乎所有 Android 特性，目前已经被大家广泛使用。

## 0.9.1
##### 1. 最初，为了让程序更加健壮，当我们通过无效的 Intent 来启动插件中的 Activity，这个时候程序不会报错。尽管我们的初衷是好的，但现在我们觉得这种方式太过友好了，不利于bug的排查。

```改动```：现在，启动无效的 Activity 会直接抛出 ActivityNotFoundException。

##### 2. 最初，为了性能考虑，当启动插件中的四大组件时，要求 Intent 中的包名必须和目标插件一致，否则就会抛出 RuntimeException。从反馈来看，这貌似给大家造成了一些困扰，所以我们优化了这一块，采用全局搜索策略，牺牲一点点效率来解决这个问题。

```改动```：现在，启动插件中的组件将不再依赖 Intent 中的包名。

```新增```：现在，如果一个组件（Activity/Service/Receiver）在宿主和插件中同时存在，那么只有插件中的组件会被唤起。

##### 3. 现在 VirtualAPK 将全面支持 Android O。

```改动```：兼容 Android O。

##### 4. 修复了一个 bug，该bug曾经导致：如果先后加载了A和B两个插件，并且存在通过A的Resources对象来访问B中资源这种情形，这个时候资源访问就会失败，造成 Crash。

```改动```：我们修复了这个bug，使得通过任何 Resources 对象均可以访问所有插件以及宿主的资源。

##### 5. 现在 VirtualAPK 将开始 hook Android N 的 Resources 对象，尽管这个改动是多余的。事实上，从Android L开始，仅仅替换ContextImpl和PluginContext中的资源就可以满足绝大多数使用场景，为了避免开发者心存疑问，我们统一了这一行为，任何版本都hook资源。

```改动```：hook Android N 的资源，尽管是多余的。