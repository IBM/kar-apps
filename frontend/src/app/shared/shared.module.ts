import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HeaderComponent } from './components/header/header.component';
import { FooterComponent } from './components/footer/footer.component';
import { SidebarComponent } from './components/sidebar/sidebar.component';
import { MatDividerModule} from '@angular/material/divider';
import { MatToolbarModule} from '@angular/material/toolbar';
import { MatIconModule} from '@angular/material/icon';
import { MatButtonModule} from '@angular/material/button';
import { MatMenuModule} from '@angular/material/menu';
import { MatListModule} from '@angular/material/list';
import { FlexLayoutModule} from '@angular/flex-layout';
import { RouterModule } from '@angular/router';
import { HttpClientModule } from '@angular/common/http';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSelectModule } from '@angular/material/select';
import {MatDialogModule} from "@angular/material/dialog";
import { MatSortModule } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
//import { MatHorizontalStepper } from '@angular/material/stepper';
import {MatProgressBarModule} from '@angular/material/progress-bar';
@NgModule({
  declarations: [
    HeaderComponent, FooterComponent, SidebarComponent
  ],
  imports: [
    CommonModule,
    MatDividerModule,
    MatToolbarModule,
    MatIconModule,
    MatButtonModule,
    FlexLayoutModule,
    MatMenuModule,
    MatListModule,
    RouterModule,
    HttpClientModule,
    MatCheckboxModule,
    MatSelectModule,
    MatDialogModule,
    MatSortModule,
    MatTableModule,
    MatProgressBarModule
    //MatHorizontalStepper,
  ],
  exports: [
    HeaderComponent, FooterComponent, SidebarComponent, MatCheckboxModule, MatSortModule, MatIconModule,MatProgressBarModule
  ]
})
export class SharedModule { }
