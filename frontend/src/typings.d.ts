import { CoreEnvironment } from "@angular/compiler/src/compiler_facade_interface";

declare var process: Process;

interface Process {
    env: Env
}

interface Env {
    REEFER_REST_HOST: string
}

interface GlobalEnvironment {
    process: Process;
}