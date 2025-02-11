# How to Run and Configure a Skip Graph Node

In order to configure a skip graph node, a file named `config.yaml` must be prepared by
providing the necessary information for a skip graph node to function. The required
parameters are:

* The Underlay Protocol to be used.
* Port
* Introducer Address
* Name ID Assignment Protocol
* Name ID value if Assignment Protocol is self-assgined
* Numerical ID Assignment Protocol
* Numerical ID value if Assignment Protocol is self-assigned
* Storage Path

After the configuration file is ready, the skip graph node can be run by running the executable
provided with the project.

## A parser for `config.yaml` is not implemented.

You have to implement a parser. The parser must create and have objects, such as Underlay, SkipNodeInterface, SkipNodeIdentity, and LookupTable (which is created by using LookupTableFactory). Then, the parser's attributes can be used to create objects, such as MiddleLayer and SkipNode. 

## The SkipNodeTest class is used only to test this implementation locally.

There are many useful example codes.

# How to Interact with Skip Graph Node

After setting up a Skip Graph process in the network and having it running, the user can
interact with the Skip Graph Node in an online manner. While the Node is running, options
for possible queries will appear to the user. These options are:

## Search by Numerical ID

After the user picks this option, the interactor API prompts the user to provide the target
numerical ID to conduct the search. Then, the query is executed from the designated skip 
node as the source of the query, and finally, it prints to the user the information of 
the node returned as the result of the query.

## Search by Name ID

After the user picks this option, the interactor API prompts the user to privde the target
name ID to conduct the search, which can be either a binary string, or a hexadecimal number
denoting the bytes of the name ID. Then, the query is executed  from th desginated skip node
as the source of the query, and finally, it prints to the user the information of the node
returned as the result of the query.    

# How to Develop and Deploy Customized Underlay Protocols

In order to create a customized underlay that can be placed in use for the Skip Graph, 
a class that extends `Underlay` must be implemented. More specifically, the functions
`initUnderlay()`, `sendMessage()`, and `terminate()` should be implemented specific to
the customized underlay.

### `initUnderaly(port)`

Initializes the underlay

### `sendMessage(address, port, requestType, requestParameters)`

Sends a request through the network and awaits the result to be returned.

### `terminate()`

Shuts Down the underlay.