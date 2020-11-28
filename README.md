# Anonymous

This is a reimplementation of [the official Android frontend of 无可奉告](https://github.com/TairanHe/SJTU-Anonymous_Forum), with the following improvements:

+ A more concise structure in pure Kotlin
+ A more stable and fast behavior thanks to Android's [ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel) and Kotlin's [Coroutines](https://developer.android.com/kotlin/coroutines)
+ A more concise UI design
+ More fluent animations and transitions
+ Closer to Google's [Material Design](https://material.io/)

Note that this repo is currently **unofficial**, which

+ May lack some detailed functions
+ Have not been fully tested

### Major known issues

+ Missing `Report Blog`, `Reply in Reversed Order`, `Logout`, `Unlike`, `About WKFG and Anonymous`
+ Losing exit transition after reenter
+ Login activity subject to change

Feel free to post your ideas in the Github [issue](https://github.com/wu-qing-157/Anonymous/issues) page.
But remember that this repo is *unofficial* and in *alpha-alpha* test stage.

### Future

If some *major* change to 无可奉告 is needed in the future:
+ Changes will be made only to this implementation
+ This implementation will be set as the official one

### Releases

Please refer to the GitHub [release](https://github.com/wu-qing-157/Anonymous/releases) page.

This repo is in *alpha-alpha* test stage, so currently has no releases.
There will be some *alpha* releases as an unofficial implementation in the future.

### Acknowledgement

The most sincere gratitude to the 无可奉告 team, especially to the contributors of [the official Android frontend](https://github.com/TairanHe/SJTU-Anonymous_Forum).
Some design in my implementation is greatly inspired from the frontend(s) provided by them.