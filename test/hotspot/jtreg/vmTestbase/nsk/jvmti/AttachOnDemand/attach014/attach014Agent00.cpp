/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <jni.h>
#include <jvmti.h>
#include <aod.h>
#include <jvmti_aod.h>

extern "C" {

#define LOADED_CLASS_NAME "Lnsk/jvmti/AttachOnDemand/attach014/ClassToLoad;"

/*
 * Expected agent work scenario:
 *  - during initialization agent enables ClassLoad events
 *  - target application loads class 'ClassToLoad'
 *  - agent receives ClassLoad event for this class, calls DisposeEnvironment and finishes work
 */

static Options* options = NULL;
static const char* agentName;

void JNICALL
classLoadHandler(jvmtiEnv *jvmti,
        JNIEnv* jni,
        jthread thread,
        jclass klass) {
    char className[MAX_STRING_LENGTH];

    if (!nsk_jvmti_aod_getClassName(jvmti, klass, className)) {
        nsk_jvmti_aod_disableEventAndFinish(agentName, JVMTI_EVENT_CLASS_LOAD, 0, jvmti, jni);
        return;
    }

    NSK_DISPLAY2("%s: ClassLoad event was received for class '%s'\n", agentName, className);

    if (!strcmp(className, LOADED_CLASS_NAME)) {
        int success = 1;

        if (!nsk_jvmti_aod_disableEvent(jvmti, JVMTI_EVENT_CLASS_LOAD))
            success = 0;

        if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB1(DisposeEnvironment, jvmti))) {
            success = 0;
            NSK_COMPLAIN1("%s: failed to dispose environment\n", agentName);
        } else {
            NSK_DISPLAY1("%s: jvmti env was disposed\n", agentName);
        }

        nsk_aod_agentFinished(jni, agentName, success);
    }
}


#ifdef STATIC_BUILD
JNIEXPORT jint JNI_OnLoad_attach014Agent00(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif

JNIEXPORT jint JNICALL
#ifdef STATIC_BUILD
Agent_OnAttach_attach014Agent00(JavaVM *vm, char *optionsString, void *reserved)
#else
Agent_OnAttach(JavaVM *vm, char *optionsString, void *reserved)
#endif
{
    jvmtiEventCallbacks eventCallbacks;
    jvmtiEnv* jvmti = NULL;
    JNIEnv* jni = NULL;

    if (!NSK_VERIFY((options = (Options*) nsk_aod_createOptions(optionsString)) != NULL))
        return JNI_ERR;

    agentName = nsk_aod_getOptionValue(options, NSK_AOD_AGENT_NAME_OPTION);

    if ((jni = (JNIEnv*) nsk_aod_createJNIEnv(vm)) == NULL)
        return NSK_FALSE;

    if (!NSK_VERIFY((jvmti = nsk_jvmti_createJVMTIEnv(vm, reserved)) != NULL))
        return JNI_ERR;

    memset(&eventCallbacks,0, sizeof(eventCallbacks));
    eventCallbacks.ClassLoad = classLoadHandler;
    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB3(SetEventCallbacks, jvmti, &eventCallbacks, sizeof(eventCallbacks))) ) {
        return JNI_ERR;
    }

    if (!(nsk_jvmti_aod_enableEvent(jvmti, JVMTI_EVENT_CLASS_LOAD))) {
        return JNI_ERR;
    }

    NSK_DISPLAY1("%s: initialization was done\n", agentName);

    if (!NSK_VERIFY(nsk_aod_agentLoaded(jni, agentName)))
        return JNI_ERR;

    return JNI_OK;
}

}
