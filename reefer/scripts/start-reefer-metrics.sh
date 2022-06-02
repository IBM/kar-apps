#!/bin/bash

#
# Copyright IBM Corporation 2020,2022
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


# check that metrics node is started
check=$(k3d node list | grep metrics-master | awk '{print $1}')
if [ -z $check ];
then
    echo "Please run start-kar-metrics.sh and before this script"
    exit 1
fi

set -e

# change to this directory
cd $(dirname "$0")

kubectl apply -f reefer-metrics.yaml
kubectl apply -f reefer-dashboard.yaml -n prometheus
