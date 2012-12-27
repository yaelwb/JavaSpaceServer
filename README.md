JavaSpaceServer
===============

A project for parallel and distributed programming course (Spring of 2004).  
An objects storage server, following the principles of "JavaSpace" - 
http://javaspaces.homestead.com, supplying Read, Write, Take and Notify actions.

Objects could have a class, and a set of fields, for which they could have values as well.
Not all fields had to have assigned values. Not all objects had to have fields at all.
Field names could be used by different classes, for example both class Dog and class Chair 
could have a field named "Color", but the acceptable values might differ.
Class hirerchy was kept too - a Read action for a Dog with Color=Brown could return a brown poodle if exists, 
but not a brown cat.

Apart from the four actions, we were required to supply a specified locking mechanism.
The number of locks required to perform an action had to be proportional to the number of fields.
That is why we used a multilevel data base: DB->Fields->Values->Classes->Objects
