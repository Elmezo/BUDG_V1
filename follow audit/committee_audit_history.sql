-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Nov 23, 2025 at 01:53 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `project`
--

-- --------------------------------------------------------

--
-- Table structure for table `committee_audit_history`
--

CREATE TABLE `committee_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `committee_audit_history`
--

INSERT INTO `committee_audit_history` (`id`, `auditidpk`, `object`, `event`, `updateType`, `field`, `from`, `to`, `author`, `date`, `lastChange`) VALUES
(14, 7, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Committee Steward', 'Alice Smith', '2025-10-22 14:05:06', '2025-10-22 14:05:06'),
(14, 8, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-22 14:05:06', '2025-10-22 14:05:06'),
(14, 9, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-22 14:05:06', '2025-10-22 14:05:06'),
(15, 17, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Committee Steward', 'Alice Smith', '2025-10-22 14:21:32', '2025-10-22 14:21:32'),
(15, 18, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-22 14:21:32', '2025-10-22 14:21:32'),
(15, 19, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-22 14:21:32', '2025-10-22 14:21:32'),
(16, 20, 'Committee', 'Details', 'Added', 'Primary Name', NULL, 'FGSDFGSDG', 'Alice Smith', '2025-10-22 14:23:25', '2025-10-22 14:23:25'),
(16, 21, 'Committee', 'Details', 'Added', 'Description', NULL, 'ASDGDFGDF', 'Alice Smith', '2025-10-22 14:23:25', '2025-10-22 14:23:25'),
(16, 22, 'Committee', 'Details', 'Added', 'Reference Number', NULL, 'CM012', 'Alice Smith', '2025-10-22 14:23:25', '2025-10-22 14:23:25'),
(16, 23, 'Committee', 'Details', 'Added', 'Parent Committee', NULL, 'dsa2', 'Alice Smith', '2025-10-22 14:23:25', '2025-10-22 14:23:25'),
(16, 24, 'Committee', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Public', 'Alice Smith', '2025-10-22 14:23:25', '2025-10-22 14:23:25'),
(16, 25, 'Committee', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-22 14:23:25', '2025-10-22 14:23:25'),
(16, 26, 'Committee', 'Details', 'Status Change', 'Lifecycle', NULL, 'committee_lifecycle ', 'Alice Smith', '2025-10-22 14:23:25', '2025-10-22 14:23:25'),
(16, 27, 'Committee', 'Details', 'Added', 'Committee Type', NULL, 'committee_type', 'Alice Smith', '2025-10-22 14:23:25', '2025-10-22 14:23:25'),
(16, 28, 'Committee', 'Details', 'Status Change', 'Classification', NULL, 'committee_classification', 'Alice Smith', '2025-10-22 14:23:25', '2025-10-22 14:23:25'),
(16, 29, 'Committee', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-22 14:23:25', '2025-10-22 14:23:25'),
(16, 30, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Committee Steward', 'Alice Smith', '2025-10-22 14:23:25', '2025-10-22 14:23:25'),
(16, 31, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-22 14:23:25', '2025-10-22 14:23:25'),
(16, 32, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-22 14:23:25', '2025-10-22 14:23:25'),
(17, 33, 'Committee', 'Details', 'Added', 'Primary Name', NULL, 'يبلايبايب', 'Alice Smith', '2025-10-22 14:33:44', '2025-10-22 14:33:44'),
(17, 34, 'Committee', 'Details', 'Added', 'Description', NULL, 'fgertyhdg', 'Alice Smith', '2025-10-22 14:33:44', '2025-10-22 14:33:44'),
(17, 35, 'Committee', 'Details', 'Added', 'Reference Number', NULL, 'CM013', 'Alice Smith', '2025-10-22 14:33:44', '2025-10-22 14:33:44'),
(17, 36, 'Committee', 'Details', 'Added', 'Parent Committee', NULL, 'NNVCBN', 'Alice Smith', '2025-10-22 14:33:44', '2025-10-22 14:33:44'),
(17, 37, 'Committee', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Private', 'Alice Smith', '2025-10-22 14:33:44', '2025-10-22 14:33:44'),
(17, 38, 'Committee', 'Details', 'Status Change', 'Status', NULL, 'Inactive', 'Alice Smith', '2025-10-22 14:33:44', '2025-10-22 14:33:44'),
(17, 39, 'Committee', 'Details', 'Status Change', 'Lifecycle', NULL, 'committee_lifecycle ', 'Alice Smith', '2025-10-22 14:33:44', '2025-10-22 14:33:44'),
(17, 40, 'Committee', 'Details', 'Added', 'Committee Type', NULL, 'committee_type', 'Alice Smith', '2025-10-22 14:33:44', '2025-10-22 14:33:44'),
(17, 41, 'Committee', 'Details', 'Status Change', 'Classification', NULL, 'committee_classification', 'Alice Smith', '2025-10-22 14:33:44', '2025-10-22 14:33:44'),
(17, 42, 'Committee', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-22 14:33:44', '2025-10-22 14:33:44'),
(17, 43, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Committee Steward', 'Alice Smith', '2025-10-22 14:33:44', '2025-10-22 14:33:44'),
(17, 44, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-22 14:33:44', '2025-10-22 14:33:44'),
(17, 45, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-22 14:33:44', '2025-10-22 14:33:44'),
(19, 46, 'Committee', 'Details', 'Added', 'Primary Name', NULL, 'ليبليبل', 'Alice Smith', '2025-10-23 15:06:34', '2025-10-23 15:06:34'),
(19, 47, 'Committee', 'Details', 'Added', 'Description', NULL, 'يبليبلي', 'Alice Smith', '2025-10-23 15:06:34', '2025-10-23 15:06:34'),
(19, 48, 'Committee', 'Details', 'Added', 'Reference Number', NULL, 'يبل', 'Alice Smith', '2025-10-23 15:06:34', '2025-10-23 15:06:34'),
(19, 49, 'Committee', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Public', 'Alice Smith', '2025-10-23 15:06:34', '2025-10-23 15:06:34'),
(19, 50, 'Committee', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 15:06:34', '2025-10-23 15:06:34'),
(19, 51, 'Committee', 'Details', 'Status Change', 'Lifecycle', NULL, 'committee_lifecycle ', 'Alice Smith', '2025-10-23 15:06:34', '2025-10-23 15:06:34'),
(19, 52, 'Committee', 'Details', 'Status Change', 'Committee Type', NULL, 'committee_type', 'Alice Smith', '2025-10-23 15:06:34', '2025-10-23 15:06:34'),
(19, 53, 'Committee', 'Details', 'Status Change', 'Classification', NULL, 'committee_classification', 'Alice Smith', '2025-10-23 15:06:34', '2025-10-23 15:06:34'),
(19, 54, 'Committee', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 15:06:34', '2025-10-23 15:06:34'),
(19, 55, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Committee Steward', 'Alice Smith', '2025-10-23 15:06:34', '2025-10-23 15:06:34'),
(19, 56, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 15:06:34', '2025-10-23 15:06:34'),
(19, 57, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 15:06:34', '2025-10-23 15:06:34'),
(20, 58, 'Committee', 'Details', 'Added', 'Primary Name', NULL, 'hfghfgh', 'Alice Smith', '2025-10-23 16:30:12', '2025-10-23 16:30:12'),
(20, 59, 'Committee', 'Details', 'Added', 'Description', NULL, 'dfghdfg', 'Alice Smith', '2025-10-23 16:30:12', '2025-10-23 16:30:12'),
(20, 60, 'Committee', 'Details', 'Added', 'Reference Number', NULL, 'fgdh', 'Alice Smith', '2025-10-23 16:30:12', '2025-10-23 16:30:12'),
(20, 61, 'Committee', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Public', 'Alice Smith', '2025-10-23 16:30:12', '2025-10-23 16:30:12'),
(20, 62, 'Committee', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:30:12', '2025-10-23 16:30:12'),
(20, 63, 'Committee', 'Details', 'Status Change', 'Lifecycle', NULL, 'committee_lifecycle ', 'Alice Smith', '2025-10-23 16:30:12', '2025-10-23 16:30:12'),
(20, 64, 'Committee', 'Details', 'Status Change', 'Committee Type', NULL, 'committee_type', 'Alice Smith', '2025-10-23 16:30:12', '2025-10-23 16:30:12'),
(20, 65, 'Committee', 'Details', 'Status Change', 'Classification', NULL, 'committee_classification', 'Alice Smith', '2025-10-23 16:30:12', '2025-10-23 16:30:12'),
(20, 66, 'Committee', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:30:12', '2025-10-23 16:30:12'),
(20, 67, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Committee Steward', 'Alice Smith', '2025-10-23 16:30:12', '2025-10-23 16:30:12'),
(20, 68, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:30:12', '2025-10-23 16:30:12'),
(20, 69, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:30:12', '2025-10-23 16:30:12'),
(21, 70, 'Committee', 'Details', 'Added', 'Primary Name', NULL, 'esraa_test', 'Alice Smith', '2025-10-23 16:31:47', '2025-10-23 16:31:47'),
(21, 71, 'Committee', 'Details', 'Added', 'Description', NULL, 'vv', 'Alice Smith', '2025-10-23 16:31:47', '2025-10-23 16:31:47'),
(21, 72, 'Committee', 'Details', 'Added', 'Reference Number', NULL, 'CM015', 'Alice Smith', '2025-10-23 16:31:47', '2025-10-23 16:31:47'),
(21, 73, 'Committee', 'Details', 'Added', 'Parent Committee', NULL, 'ahmed', 'Alice Smith', '2025-10-23 16:31:47', '2025-10-23 16:31:47'),
(21, 74, 'Committee', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Public', 'Alice Smith', '2025-10-23 16:31:47', '2025-10-23 16:31:47'),
(21, 75, 'Committee', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:31:47', '2025-10-23 16:31:47'),
(21, 76, 'Committee', 'Details', 'Status Change', 'Lifecycle', NULL, 'committee_lifecycle ', 'Alice Smith', '2025-10-23 16:31:47', '2025-10-23 16:31:47'),
(21, 77, 'Committee', 'Details', 'Status Change', 'Committee Type', NULL, 'committee_type', 'Alice Smith', '2025-10-23 16:31:47', '2025-10-23 16:31:47'),
(21, 78, 'Committee', 'Details', 'Status Change', 'Classification', NULL, 'committee_classification', 'Alice Smith', '2025-10-23 16:31:47', '2025-10-23 16:31:47'),
(21, 79, 'Committee', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:31:47', '2025-10-23 16:31:47'),
(21, 80, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Committee Steward', 'Alice Smith', '2025-10-23 16:31:47', '2025-10-23 16:31:47'),
(21, 81, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:31:47', '2025-10-23 16:31:47'),
(21, 82, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:31:47', '2025-10-23 16:31:47'),
(22, 83, 'Committee', 'Details', 'Added', 'Primary Name', NULL, 'etuh', 'Alice Smith', '2025-10-26 15:42:40', '2025-10-26 15:42:40'),
(22, 84, 'Committee', 'Details', 'Added', 'Description', NULL, 'hfdghdfg', 'Alice Smith', '2025-10-26 15:42:40', '2025-10-26 15:42:40'),
(22, 85, 'Committee', 'Details', 'Added', 'Reference Number', NULL, 'rhf', 'Alice Smith', '2025-10-26 15:42:40', '2025-10-26 15:42:40'),
(22, 86, 'Committee', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Public', 'Alice Smith', '2025-10-26 15:42:40', '2025-10-26 15:42:40'),
(22, 87, 'Committee', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-26 15:42:40', '2025-10-26 15:42:40'),
(22, 88, 'Committee', 'Details', 'Status Change', 'Lifecycle', NULL, 'committee_lifecycle ', 'Alice Smith', '2025-10-26 15:42:40', '2025-10-26 15:42:40'),
(22, 89, 'Committee', 'Details', 'Status Change', 'Committee Type', NULL, 'committee_type', 'Alice Smith', '2025-10-26 15:42:40', '2025-10-26 15:42:40'),
(22, 90, 'Committee', 'Details', 'Status Change', 'Classification', NULL, 'committee_classification', 'Alice Smith', '2025-10-26 15:42:40', '2025-10-26 15:42:40'),
(22, 91, 'Committee', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-26 15:42:40', '2025-10-26 15:42:40'),
(22, 92, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Committee Steward', 'Alice Smith', '2025-10-26 15:42:40', '2025-10-26 15:42:40'),
(22, 93, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-26 15:42:40', '2025-10-26 15:42:40'),
(22, 94, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-26 15:42:40', '2025-10-26 15:42:40'),
(23, 95, 'Committee', 'Details', 'Added', 'Primary Name', NULL, 'سيبيس', 'Alice Smith', '2025-10-26 15:53:19', '2025-10-26 15:53:19'),
(23, 96, 'Committee', 'Details', 'Added', 'Description', NULL, 'يسبسيب', 'Alice Smith', '2025-10-26 15:53:19', '2025-10-26 15:53:19'),
(23, 97, 'Committee', 'Details', 'Added', 'Reference Number', NULL, 'سيب', 'Alice Smith', '2025-10-26 15:53:19', '2025-10-26 15:53:19'),
(23, 98, 'Committee', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Public', 'Alice Smith', '2025-10-26 15:53:19', '2025-10-26 15:53:19'),
(23, 99, 'Committee', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-26 15:53:19', '2025-10-26 15:53:19'),
(23, 100, 'Committee', 'Details', 'Status Change', 'Lifecycle', NULL, 'committee_lifecycle ', 'Alice Smith', '2025-10-26 15:53:19', '2025-10-26 15:53:19'),
(23, 101, 'Committee', 'Details', 'Status Change', 'Committee Type', NULL, 'committee_type', 'Alice Smith', '2025-10-26 15:53:19', '2025-10-26 15:53:19'),
(23, 102, 'Committee', 'Details', 'Status Change', 'Classification', NULL, 'committee_classification', 'Alice Smith', '2025-10-26 15:53:19', '2025-10-26 15:53:19'),
(23, 103, 'Committee', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-26 15:53:19', '2025-10-26 15:53:19'),
(23, 104, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Committee Steward', 'Alice Smith', '2025-10-26 15:53:20', '2025-10-26 15:53:20'),
(23, 105, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-26 15:53:20', '2025-10-26 15:53:20'),
(23, 106, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-26 15:53:20', '2025-10-26 15:53:20'),
(24, 107, 'Committee', 'Details', 'Added', 'Primary Name', NULL, 'jhkgjkm', 'Alice Smith', '2025-10-27 14:22:45', '2025-10-27 14:22:45'),
(24, 108, 'Committee', 'Details', 'Added', 'Description', NULL, 'bsdfhd', 'Alice Smith', '2025-10-27 14:22:45', '2025-10-27 14:22:45'),
(24, 109, 'Committee', 'Details', 'Added', 'Reference Number', NULL, 'kgjbn', 'Alice Smith', '2025-10-27 14:22:45', '2025-10-27 14:22:45'),
(24, 110, 'Committee', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Public', 'Alice Smith', '2025-10-27 14:22:45', '2025-10-27 14:22:45'),
(24, 111, 'Committee', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-27 14:22:45', '2025-10-27 14:22:45'),
(24, 112, 'Committee', 'Details', 'Status Change', 'Lifecycle', NULL, 'committee_lifecycle ', 'Alice Smith', '2025-10-27 14:22:45', '2025-10-27 14:22:45'),
(24, 113, 'Committee', 'Details', 'Status Change', 'Committee Type', NULL, 'committee_type', 'Alice Smith', '2025-10-27 14:22:45', '2025-10-27 14:22:45'),
(24, 114, 'Committee', 'Details', 'Status Change', 'Classification', NULL, 'committee_classification', 'Alice Smith', '2025-10-27 14:22:45', '2025-10-27 14:22:45'),
(24, 115, 'Committee', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-27 14:22:45', '2025-10-27 14:22:45'),
(24, 116, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Committee Steward', 'Alice Smith', '2025-10-27 14:22:45', '2025-10-27 14:22:45'),
(24, 117, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-27 14:22:45', '2025-10-27 14:22:45'),
(24, 118, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-27 14:22:45', '2025-10-27 14:22:45'),
(24, 120, 'Committee', 'Details', 'Updated', 'Primary Name', 'jhkgjkm2', 'jhkgjkm1112', 'Alice Smith', '2025-10-27 14:32:28', '2025-10-27 14:32:28'),
(24, 121, 'Committee', 'Details', 'Updated', 'Description', 'bsdfhd2', 'bsdfhd', 'Alice Smith', '2025-10-27 14:32:40', '2025-10-27 14:32:40'),
(24, 122, 'Committee', 'Details', 'Updated', 'Parent Committee', NULL, 'COM2', 'Alice Smith', '2025-10-27 14:32:40', '2025-10-27 14:32:40'),
(25, 123, 'Committee', 'Details', 'Added', 'Primary Name', NULL, 'committee test4', 'Alice Smith', '2025-11-09 13:24:56', '2025-11-09 13:24:56'),
(25, 124, 'Committee', 'Details', 'Added', 'Description', NULL, '9/11', 'Alice Smith', '2025-11-09 13:24:56', '2025-11-09 13:24:56'),
(25, 125, 'Committee', 'Details', 'Added', 'Reference Number', NULL, 'CM016', 'Alice Smith', '2025-11-09 13:24:56', '2025-11-09 13:24:56'),
(25, 126, 'Committee', 'Details', 'Added', 'Parent Committee', NULL, 'COM3', 'Alice Smith', '2025-11-09 13:24:56', '2025-11-09 13:24:56'),
(25, 127, 'Committee', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Private', 'Alice Smith', '2025-11-09 13:24:56', '2025-11-09 13:24:56'),
(25, 128, 'Committee', 'Details', 'Status Change', 'Status', NULL, 'Inactive', 'Alice Smith', '2025-11-09 13:24:56', '2025-11-09 13:24:56'),
(25, 129, 'Committee', 'Details', 'Status Change', 'Lifecycle', NULL, 'committee_lifecycle ', 'Alice Smith', '2025-11-09 13:24:56', '2025-11-09 13:24:56'),
(25, 130, 'Committee', 'Details', 'Status Change', 'Committee Type', NULL, 'committee_type', 'Alice Smith', '2025-11-09 13:24:56', '2025-11-09 13:24:56'),
(25, 131, 'Committee', 'Details', 'Status Change', 'Classification', NULL, 'committee_classification', 'Alice Smith', '2025-11-09 13:24:56', '2025-11-09 13:24:56'),
(25, 132, 'Committee', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 13:24:56', '2025-11-09 13:24:56'),
(25, 133, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Committee Steward', 'Alice Smith', '2025-11-09 13:24:56', '2025-11-09 13:24:56'),
(25, 134, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-09 13:24:56', '2025-11-09 13:24:56'),
(25, 135, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 13:24:56', '2025-11-09 13:24:56'),
(25, 136, 'Stakeholder', 'delete', 'Deleted', 'Name', 'Alice Smith', NULL, 'System', '2025-11-09 13:25:58', '2025-11-09 13:25:58'),
(25, 137, 'Stakeholder', 'delete', 'Deleted', 'Role', 'Committee Steward', NULL, 'System', '2025-11-09 13:25:58', '2025-11-09 13:25:58'),
(25, 138, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Active', NULL, 'System', '2025-11-09 13:25:58', '2025-11-09 13:25:58'),
(25, 139, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Eve Davis', 'Alice Smith', '2025-11-09 13:25:58', '2025-11-09 13:25:58'),
(25, 140, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Committee Steward', 'Alice Smith', '2025-11-09 13:25:58', '2025-11-09 13:25:58'),
(25, 141, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-09 13:25:58', '2025-11-09 13:25:58'),
(26, 142, 'Committee', 'Details', 'Added', 'Primary Name', NULL, 'dasd', 'Alice Smith', '2025-11-19 13:22:44', '2025-11-19 13:22:44'),
(26, 143, 'Committee', 'Details', 'Added', 'Description', NULL, 'sdasd', 'Alice Smith', '2025-11-19 13:22:44', '2025-11-19 13:22:44'),
(26, 144, 'Committee', 'Details', 'Added', 'Reference Number', NULL, 'CM-1', 'Alice Smith', '2025-11-19 13:22:44', '2025-11-19 13:22:44'),
(26, 145, 'Committee', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Public', 'Alice Smith', '2025-11-19 13:22:44', '2025-11-19 13:22:44'),
(26, 146, 'Committee', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-11-19 13:22:44', '2025-11-19 13:22:44'),
(26, 147, 'Committee', 'Details', 'Status Change', 'Lifecycle', NULL, 'committee_lifecycle ', 'Alice Smith', '2025-11-19 13:22:44', '2025-11-19 13:22:44'),
(26, 148, 'Committee', 'Details', 'Status Change', 'Committee Type', NULL, 'committee_type', 'Alice Smith', '2025-11-19 13:22:44', '2025-11-19 13:22:44'),
(26, 149, 'Committee', 'Details', 'Status Change', 'Classification', NULL, 'committee_classification', 'Alice Smith', '2025-11-19 13:22:44', '2025-11-19 13:22:44'),
(26, 150, 'Committee', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-19 13:22:44', '2025-11-19 13:22:44');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `committee_audit_history`
--
ALTER TABLE `committee_audit_history`
  ADD PRIMARY KEY (`id`,`auditidpk`),
  ADD UNIQUE KEY `auditidpk` (`auditidpk`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `committee_audit_history`
--
ALTER TABLE `committee_audit_history`
  MODIFY `auditidpk` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=151;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `committee_audit_history`
--
ALTER TABLE `committee_audit_history`
  ADD CONSTRAINT `Committee_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `committee` (`ID`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
